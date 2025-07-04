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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

        // Calculate tree edit distance using Zhang-Shasha algorithm
        ZhangShashaResult result = calculateZhangShashaEditDistance(currentTree, newTree);
        Set<String> addedNodeIds = result.addedNodes;
        Set<String> modifiedNodeIds = result.modifiedNodes;

        // Mark suggested nodes
        List<FlowNode> updatedNodes = markSuggestedNodes(newDiagram.nodes(), addedNodeIds, modifiedNodeIds);

        return new Diagram(newDiagram.fileName(), updatedNodes, newDiagram.connections());
    }

    /**
     * Result of Zhang-Shasha tree edit distance calculation.
     */
    private static class ZhangShashaResult {

        final double editDistance;
        final Set<String> addedNodes;
        final Set<String> modifiedNodes;

        ZhangShashaResult(double editDistance, Set<String> addedNodes, Set<String> modifiedNodes) {
            this.editDistance = editDistance;
            this.addedNodes = addedNodes;
            this.modifiedNodes = modifiedNodes;
        }
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

    /**
     * Calculates tree edit distance using the Zhang-Shasha algorithm. This implementation follows the original
     * Zhang-Shasha algorithm for computing minimum edit distance and determines suggested nodes (both changed and
     * added).
     */
    private static ZhangShashaResult calculateZhangShashaEditDistance(TreeNode tree1, TreeNode tree2) {
        // Preprocess trees: assign postorder numbers and compute leftmost leaf descendants
        List<TreeNode> postorder1 = new ArrayList<>();
        List<TreeNode> postorder2 = new ArrayList<>();

        assignPostorderNumbers(tree1, postorder1);
        assignPostorderNumbers(tree2, postorder2);

        computeLeftmostLeafDescendants(tree1);
        computeLeftmostLeafDescendants(tree2);

        int size1 = postorder1.size();
        int size2 = postorder2.size();

        // Initialize the forest distance matrix
        double[][] forestDist = new double[size1 + 1][size2 + 1];

        // Create signature sets for determining added nodes
        Set<String> tree1Signatures = new HashSet<>();
        for (TreeNode node : postorder1) {
            if (node.isFlowNode) {
                tree1Signatures.add(node.signature);
            }
        }

        Set<String> addedNodes = new HashSet<>();
        for (TreeNode node : postorder2) {
            if (node.isFlowNode && !tree1Signatures.contains(node.signature)) {
                addedNodes.add(node.id);
            }
        }

        Set<String> modifiedNodes = new HashSet<>();
        // Zhang-Shasha algorithm main computation
        double editDistance = computeEditDistance(postorder1, postorder2, forestDist, modifiedNodes);

        return new ZhangShashaResult(editDistance, addedNodes, modifiedNodes);
    }

    /**
     * Assigns postorder numbers to tree nodes (Zhang-Shasha preprocessing step).
     */
    private static int assignPostorderNumbers(TreeNode node, List<TreeNode> postorderList) {
        int maxNumber = 0;

        for (TreeNode child : node.children) {
            maxNumber = Math.max(maxNumber, assignPostorderNumbers(child, postorderList));
        }

        node.postorderNumber = maxNumber;
        postorderList.add(node);

        return maxNumber + 1;
    }

    /**
     * Computes leftmost leaf descendants for each node (Zhang-Shasha preprocessing step).
     */
    private static int computeLeftmostLeafDescendants(TreeNode node) {
        if (node.children.isEmpty()) {
            node.leftmostLeafDescendant = node.postorderNumber;
            return node.postorderNumber;
        }

        int leftmost = Integer.MAX_VALUE;
        for (TreeNode child : node.children) {
            leftmost = Math.min(leftmost, computeLeftmostLeafDescendants(child));
        }

        node.leftmostLeafDescendant = leftmost;
        return leftmost;
    }

    /**
     * Core Zhang-Shasha edit distance computation.
     */
    private static double computeEditDistance(List<TreeNode> postorder1, List<TreeNode> postorder2,
                                              double[][] forestDist, Set<String> modifiedNodes) {
        int size1 = postorder1.size();
        int size2 = postorder2.size();

        // Initialize base cases
        for (int i = 0; i <= size1; i++) {
            forestDist[i][0] = i; // Cost of deleting i nodes
        }
        for (int j = 0; j <= size2; j++) {
            forestDist[0][j] = j; // Cost of inserting j nodes
        }

        // Main Zhang-Shasha computation
        for (int i = 1; i <= size1; i++) {
            for (int j = 1; j <= size2; j++) {
                TreeNode node1 = postorder1.get(i - 1);
                TreeNode node2 = postorder2.get(j - 1);

                if (node1.leftmostLeafDescendant == i - 1 && node2.leftmostLeafDescendant == j - 1) {
                    // Both nodes are leftmost leaf descendants - compute tree distance
                    double cost = computeTreeDistance(node1, node2, modifiedNodes);
                    forestDist[i][j] = Math.min(
                            Math.min(
                                    forestDist[i - 1][j] + 1, // Delete from tree1
                                    forestDist[i][j - 1] + 1  // Insert to tree1
                            ),
                            forestDist[i - 1][j - 1] + cost // Substitute
                    );
                } else {
                    // Forest distance computation
                    forestDist[i][j] = Math.min(
                            Math.min(
                                    forestDist[i - 1][j] + 1,
                                    forestDist[i][j - 1] + 1
                            ),
                            forestDist[node1.leftmostLeafDescendant][node2.leftmostLeafDescendant] +
                                    forestDist[i][j]
                    );
                }
            }
        }

        return forestDist[size1][size2];
    }

    /**
     * Computes the cost of transforming one tree node to another. Uses only sourceCode comparison for node identity.
     */
    private static double computeTreeDistance(TreeNode node1, TreeNode node2, Set<String> modifiedNodes) {
        if (Objects.equals(node1.signature, node2.signature)) {
            return 0.0;
        } else {
            if (node1.isFlowNode && node2.isFlowNode) {
                modifiedNodes.add(node2.id);
            }
            return 1.0;
        }
    }

    /**
     * Marks nodes as suggested based on the difference analysis. Propagates suggested flag to all descendants including
     * branches and their children.
     */
    private static List<FlowNode> markSuggestedNodes(List<FlowNode> nodes, Set<String> addedNodeIds,
                                                     Set<String> modifiedNodeIds) {
        Set<String> suggestedNodeIds = new HashSet<>(modifiedNodeIds);
        return nodes.stream().map(node -> markNodeAndDescendants(node, addedNodeIds, suggestedNodeIds, false))
                .toList();
    }

    /**
     * Recursively marks a node and all its descendants as suggested if the parent is suggested.
     */
    private static FlowNode markNodeAndDescendants(FlowNode node, Set<String> addedNodeIds,
                                                   Set<String> suggestedNodeIds, boolean isParentAdded) {
        boolean isNodeAdded = isParentAdded || addedNodeIds.contains(node.id());
        boolean isNodeSuggested = isNodeAdded || suggestedNodeIds.contains(node.id());

        // Mark branches and their children
        List<Branch> updatedBranches = null;
        if (node.branches() != null) {
            updatedBranches = node.branches().stream()
                    .map(branch -> markBranchAndDescendants(branch, addedNodeIds, suggestedNodeIds, isNodeAdded))
                    .toList();
        }

        return new FlowNode(
                node.id(),
                node.metadata(),
                node.codedata(),
                node.returning(),
                updatedBranches,
                node.properties(),
                node.diagnostics(),
                node.flags(),
                isNodeSuggested
        );
    }

    /**
     * Recursively marks a branch and all its children as suggested if the parent is suggested.
     */
    private static Branch markBranchAndDescendants(Branch branch, Set<String> addedNodeIds,
                                                   Set<String> suggestedNodeIds, boolean isParentAdded) {
        // Mark branch children
        List<FlowNode> updatedChildren = null;
        if (branch.children() != null) {
            updatedChildren = branch.children().stream()
                    .map(child -> markNodeAndDescendants(child, addedNodeIds, suggestedNodeIds, isParentAdded))
                    .toList();
        }

        return new Branch(
                branch.label(),
                branch.kind(),
                branch.codedata(),
                branch.repeatable(),
                branch.properties(),
                updatedChildren,
                isParentAdded
        );
    }

}