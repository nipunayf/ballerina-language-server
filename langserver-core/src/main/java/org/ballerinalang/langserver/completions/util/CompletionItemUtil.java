/*
 *  Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
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
package org.ballerinalang.langserver.completions.util;

import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.Qualifier;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import org.ballerinalang.langserver.common.utils.CommonUtil;

import java.util.List;
import java.util.Optional;

/**
 * Utility methods for completion item building.
 *
 * @since 1.0.0
 */
public final class CompletionItemUtil {

    private static final String CONFIGURABLE_CATEGORY = "Configurable";
    private static final String LISTENER_CATEGORY = "Listener";
    private static final String CLIENT_CATEGORY = "Client";
    private static final String RECORD_CATEGORY = "Record";

    private CompletionItemUtil() {
    }

    /**
     * Get the category description for a symbol based on its type and qualifiers.
     * This method is shared between field and variable completion items.
     *
     * @param typeDescriptor type descriptor of the symbol
     * @param qualifiers     qualifiers of the symbol
     * @return {@link Optional} containing the category description if applicable
     */
    public static Optional<String> getCategoryDescription(TypeSymbol typeDescriptor, List<Qualifier> qualifiers) {
        // Check qualifiers first
        if (qualifiers.contains(Qualifier.CONFIGURABLE)) {
            return Optional.of(CONFIGURABLE_CATEGORY);
        }
        if (qualifiers.contains(Qualifier.LISTENER)) {
            return Optional.of(LISTENER_CATEGORY);
        }

        // Check type using getRawType to unwrap references and intersections
        TypeSymbol rawType = CommonUtil.getRawType(typeDescriptor);

        // Check for Client class
        if (rawType instanceof ClassSymbol classSymbol
                && classSymbol.qualifiers().contains(Qualifier.CLIENT)) {
            return Optional.of(CLIENT_CATEGORY);
        }

        // Check for Record type
        if (rawType.typeKind() == TypeDescKind.RECORD) {
            return Optional.of(RECORD_CATEGORY);
        }

        return Optional.empty();
    }
}
