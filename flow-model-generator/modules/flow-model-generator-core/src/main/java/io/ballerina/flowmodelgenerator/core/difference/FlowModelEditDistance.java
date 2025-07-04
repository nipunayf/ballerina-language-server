/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.flowmodelgenerator.core.difference;

import io.ballerina.flowmodelgenerator.core.model.Branch;
import io.ballerina.flowmodelgenerator.core.model.Diagram;
import io.ballerina.flowmodelgenerator.core.model.FlowNode;
import io.ballerina.flowmodelgenerator.core.model.NodeKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for calculating differences between two diagrams using the Zhang-Shasha tree edit distance algorithm.
 * This implementation follows the Zhang-Shasha algorithm principles for computing tree edit distance and marks nodes as
 * suggested based on detected changes. Only marks nodes as suggested, not individual properties. Properties are not
 * considered in the difference calculation. EVENT_START nodes are excluded from the comparison. Node comparison is
 * based solely on sourceCode.
 *
 * @since 1.1.0
 */
public class FlowModelEditDistance {

    /**
     * Compares two diagrams and returns a new diagram with suggested changes marked. Uses the Zhang-Shasha tree edit
     * distance algorithm to detect structural changes.
     *
     * @param currentDiagram the current (original) diagram
     * @param newDiagram     the new (modified) diagram
     * @return a new diagram with suggested flags set for changed nodes
     */
    public static Diagram computeDifferences(Diagram currentDiagram, Diagram newDiagram) {
        if (currentDiagram == null || newDiagram == null) {
            throw new IllegalStateException("Both currentDiagram and newDiagram must be non-null.");
        }

        // Build trees and node maps
        Map<String, FlowNode> currentNodeMap = new HashMap<>();
        TreeNode currentTree = buildTreeFromDiagram(currentDiagram, currentNodeMap);
        Map<String, FlowNode> newNodeMap = new HashMap<>();
        TreeNode newTree = buildTreeFromDiagram(newDiagram, newNodeMap);

        // Post-order traversal and keyroots
        List<TreeNode> currentPostorder = postOrderTraversal(currentTree);
        List<TreeNode> newPostorder = postOrderTraversal(newTree);

        calculateLeftmostLeafDescendants(currentPostorder);
        calculateLeftmostLeafDescendants(newPostorder);

        int m = currentPostorder.size();
        int n = newPostorder.size();

        int[][] dist = new int[m + 1][n + 1];
        int[][] backtrack = new int[m + 1][n + 1];

        final int DELETE = 1;
        final int INSERT = 2;
        final int UPDATE = 3;

        for (int i = 0; i <= m; i++) {
            dist[i][0] = i;
            if (i > 0) {
                backtrack[i][0] = DELETE;
            }
        }
        for (int j = 0; j <= n; j++) {
            dist[0][j] = j;
            if (j > 0) {
                backtrack[0][j] = INSERT;
            }
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                TreeNode node1 = currentPostorder.get(i - 1);
                TreeNode node2 = newPostorder.get(j - 1);

                int cost = node1.equals(node2) ? 0 : 1;

                int deletionCost = dist[i - 1][j] + 1;
                int insertionCost = dist[i][j - 1] + 1;
                int updateCost = dist[i - 1][j - 1] + cost;

                if (deletionCost <= insertionCost && deletionCost <= updateCost) {
                    dist[i][j] = deletionCost;
                    backtrack[i][j] = DELETE;
                } else if (insertionCost <= updateCost) {
                    dist[i][j] = insertionCost;
                    backtrack[i][j] = INSERT;
                } else {
                    dist[i][j] = updateCost;
                    backtrack[i][j] = UPDATE;
                }
            }
        }

        // Backtracking to find changed nodes
        Map<String, FlowNode> updatedNodes = new HashMap<>();
        Map<String, FlowNode> insertedNodes = new HashMap<>();

        int i = m;
        int j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && backtrack[i][j] == UPDATE) {
                if (dist[i][j] > dist[i - 1][j - 1]) {
                    TreeNode updatedNode = newPostorder.get(j - 1);
                    if (updatedNode.isFlowNode) {
                        updatedNodes.put(updatedNode.id, newNodeMap.get(updatedNode.id));
                    }
                }
                i--;
                j--;
            } else if (j > 0 && (i == 0 || backtrack[i][j] == INSERT)) {
                TreeNode insertedNode = newPostorder.get(j - 1);
                if (insertedNode.isFlowNode) {
                    insertedNodes.put(insertedNode.id, newNodeMap.get(insertedNode.id));
                }
                j--;
            } else if (i > 0) {
                i--;
            } else {
                break;
            }
        }

        return rebuildDiagram(newDiagram, updatedNodes, insertedNodes);
    }

    /**
     * Tree node structure for Zhang-Shasha tree edit distance calculation.
     */
    private static class TreeNode {

        final String id;
        final String signature;
        final List<TreeNode> children;
        final boolean isFlowNode;
        int leftmostLeafDescendant;
        int postorderNumber;

        TreeNode(String id, String signature, boolean isFlowNode) {
            this.id = id;
            this.signature = signature;
            this.children = new ArrayList<>();
            this.isFlowNode = isFlowNode;
            this.leftmostLeafDescendant = -1;
            this.postorderNumber = -1;
        }

        void addChild(TreeNode child) {
            this.children.add(child);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TreeNode treeNode = (TreeNode) obj;
            return Objects.equals(signature, treeNode.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(signature);
        }

        @Override
        public String toString() {
            return id + ":" + signature;
        }
    }

    private static List<TreeNode> postOrderTraversal(TreeNode root) {
        List<TreeNode> postorderNodes = new ArrayList<>();
        calculatePostOrder(root, postorderNodes);
        return postorderNodes;
    }

    private static void calculatePostOrder(TreeNode node, List<TreeNode> postorderNodes) {
        for (TreeNode child : node.children) {
            calculatePostOrder(child, postorderNodes);
        }
        node.postorderNumber = postorderNodes.size();
        postorderNodes.add(node);
    }

    private static void calculateLeftmostLeafDescendants(List<TreeNode> postorderNodes) {
        for (TreeNode node : postorderNodes) {
            if (node.children.isEmpty()) {
                node.leftmostLeafDescendant = node.postorderNumber;
            } else {
                node.leftmostLeafDescendant = node.children.get(0).leftmostLeafDescendant;
            }
        }
    }

    private static Diagram rebuildDiagram(Diagram diagram, Map<String, FlowNode> updatedNodes,
                                          Map<String, FlowNode> insertedNodes) {
        List<FlowNode> newNodes = new ArrayList<>();
        if (diagram.nodes() != null) {
            for (FlowNode node : diagram.nodes()) {
                newNodes.add(rebuildNode(node, updatedNodes, insertedNodes));
            }
        }
        return new Diagram(diagram.fileName(), newNodes, diagram.connections());
    }

    private static FlowNode rebuildNode(FlowNode node, Map<String, FlowNode> updatedNodes,
                                        Map<String, FlowNode> insertedNodes) {
        boolean suggested = updatedNodes.containsKey(node.id()) || insertedNodes.containsKey(node.id());

        List<Branch> newBranches = new ArrayList<>();
        if (node.branches() != null) {
            for (Branch branch : node.branches()) {
                newBranches.add(rebuildBranch(branch, updatedNodes, insertedNodes));
            }
        }

        return new FlowNode(
                node.id(),
                node.metadata(),
                node.codedata(),
                node.returning(),
                newBranches,
                node.properties(),
                node.diagnostics(),
                node.flags(),
                suggested
        );
    }

    private static Branch rebuildBranch(Branch branch, Map<String, FlowNode> updatedNodes,
                                        Map<String, FlowNode> insertedNodes) {
        List<FlowNode> newChildren = new ArrayList<>();
        if (branch.children() != null) {
            for (FlowNode child : branch.children()) {
                newChildren.add(rebuildNode(child, updatedNodes, insertedNodes));
            }
        }
        boolean childSuggested = newChildren.stream().anyMatch(FlowNode::suggested);
        return new Branch(
                branch.label(),
                branch.kind(),
                branch.codedata(),
                branch.repeatable(),
                branch.properties(),
                newChildren,
                childSuggested
        );
    }

    /**
     * Builds a tree representation from a diagram for Zhang-Shasha tree edit distance calculation.
     */
    private static TreeNode buildTreeFromDiagram(Diagram diagram, Map<String, FlowNode> nodeMap) {
        TreeNode root = new TreeNode("root", "root", false);
        if (diagram.nodes() != null) {
            for (FlowNode node : diagram.nodes()) {
                if (node.codedata() != null && node.codedata().node() == NodeKind.EVENT_START) {
                    continue;
                }
                nodeMap.put(node.id(), node);
                String signature = getNodeSignature(node);
                TreeNode nodeTree = new TreeNode(node.id(), signature, true);

                // Add branches as children of the flow node
                if (node.branches() != null) {
                    for (Branch branch : node.branches()) {
                        TreeNode branchTree = new TreeNode(branch.label(), getBranchSignature(branch), false);
                        nodeTree.addChild(branchTree);

                        // Add branch children as children of the branch
                        if (branch.children() != null) {
                            for (FlowNode childNode : branch.children()) {
                                addNodeToTree(branchTree, childNode, nodeMap);
                            }
                        }
                    }
                }

                root.addChild(nodeTree);
            }
        }
        return root;
    }

    /**
     * Recursively adds a node and its branches to the tree structure.
     */
    private static void addNodeToTree(TreeNode parent, FlowNode node, Map<String, FlowNode> nodeMap) {
        if (node.codedata() != null && node.codedata().node() == NodeKind.EVENT_START) {
            return;
        }

        nodeMap.put(node.id(), node);
        String signature = getNodeSignature(node);
        TreeNode nodeTree = new TreeNode(node.id(), signature, true);

        // Add branches as children of the flow node
        if (node.branches() != null) {
            for (Branch branch : node.branches()) {
                TreeNode branchTree = new TreeNode(branch.label(), getBranchSignature(branch), false);
                nodeTree.addChild(branchTree);

                // Add branch children as children of the branch
                if (branch.children() != null) {
                    for (FlowNode childNode : branch.children()) {
                        addNodeToTree(branchTree, childNode, nodeMap);
                    }
                }
            }
        }

        parent.addChild(nodeTree);
    }

    /**
     * Creates a signature for a branch based on its label and kind.
     */
    private static String getBranchSignature(Branch branch) {
        return branch.label() + ":" + branch.kind();
    }

    /**
     * Creates a signature for a node based on its source code. Node identity is determined by node.codedata.sourceCode
     * for comparison purposes.
     */
    private static String getNodeSignature(FlowNode node) {
        // Use sourceCode as the primary identifier for node comparison
        if (node.codedata() != null && node.codedata().sourceCode() != null) {
            String sourceCode = node.codedata().sourceCode().trim();
            if (node.branches() != null && !node.branches().isEmpty()) {
                int braceIndex = sourceCode.indexOf('{');
                if (braceIndex != -1) {
                    return sourceCode.substring(0, braceIndex).trim();
                }
            }
            return sourceCode;
        }

        throw new IllegalStateException("Node sourceCode is required for signature comparison");
    }

}