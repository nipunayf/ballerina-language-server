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

package io.ballerina.flowmodelgenerator.extension;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ballerina.flowmodelgenerator.extension.request.FlowModelDifferenceRequest;
import io.ballerina.modelgenerator.commons.AbstractLSTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Test cases for the flow model difference API.
 *
 * @since 1.1.0
 */
public class FlowModelDifferenceTest extends AbstractLSTest {

    @Override
    @Test(dataProvider = "data-provider")
    public void test(Path config) throws IOException {
        Path configJsonPath = configDir.resolve(config);
        TestConfig testConfig = gson.fromJson(Files.newBufferedReader(configJsonPath), TestConfig.class);

        FlowModelDifferenceRequest request = new FlowModelDifferenceRequest(testConfig.fileContentMap());
        JsonObject jsonModel = getResponseAndCloseFile(request, testConfig.projectPath()).getAsJsonObject("flowModel");

        // Assert only the file name since the absolute path may vary depending on the machine
        String balFileName = Path.of(jsonModel.getAsJsonPrimitive("fileName").getAsString()).getFileName().toString();
        JsonPrimitive testFileName = testConfig.diagram().getAsJsonPrimitive("fileName");
        boolean fileNameEquality = testFileName != null && balFileName.equals(testFileName.getAsString());
        JsonObject modifiedDiagram = jsonModel.deepCopy();
        modifiedDiagram.addProperty("fileName", balFileName);

        boolean flowEquality = modifiedDiagram.equals(testConfig.diagram());
        if (!fileNameEquality || !flowEquality) {
            TestConfig updatedConfig = new TestConfig(testConfig.fileContentMap(), testConfig.projectPath(),
                    testConfig.description(), modifiedDiagram);
//            updateConfig(configJsonPath, updatedConfig);
            compareJsonElements(modifiedDiagram, testConfig.diagram());
            Assert.fail(String.format("Failed test: '%s' (%s)", testConfig.description(), configJsonPath));
        }
    }

    @Override
    protected String getResourceDir() {
        return "flow_model_difference";
    }

    @Override
    protected Class<? extends AbstractLSTest> clazz() {
        return FlowModelDifferenceTest.class;
    }

    @Override
    protected String getApiName() {
        return "flowModelDifference";
    }

    /**
     * Represents the test configuration for the flow model difference test.
     *
     * @param fileContentMap A map where fileName is mapped to the content of the file
     * @param projectPath    The project path (used for test source resolution)
     * @param description    The description of the test
     * @param diagram        The expected diagram for the given inputs
     * @since 1.0.0
     */
    private record TestConfig(Map<String, String> fileContentMap, String projectPath, String description, 
                              JsonObject diagram) {

        public String description() {
            return description == null ? "" : description;
        }
    }
}
