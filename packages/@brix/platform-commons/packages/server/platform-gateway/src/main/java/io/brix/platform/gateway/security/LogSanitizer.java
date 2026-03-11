/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.gateway.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * logsanitizeservice
 * <p>
 * provideunifiedoflogsanitizecanforce，used forforsensitiveinformationperformmaskprocess
 * supportrequestheadersanitizeandtextinallowsanitizetwo typespattern
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Component
public class LogSanitizer {

    private final LogSanitizationProperties properties;

    public LogSanitizer(LogSanitizationProperties properties) {
        this.properties = properties;
    }

    /**
     * forrequestheadervalueperformde-
     *
     * @param headerName  requestheadername
     * @param headerValue requestheader
     * @return sanitizeafterof
     */
    public String sanitizeHeader(String headerName, String headerValue) {
        if (!properties.isEnabled() || headerValue == null) {
            return headerValue;
        }

        if (properties.isSensitiveHeader(headerName)) {
            return maskValue(headerValue);
        }

        return headerValue;
    }

    /**
     * fortextinallowperformde-
     * useconfigurationofregexexpressionpatternmatchandsanitizesensitivein
     *
     * @param text originaltext
     * @return sanitizeafteroftext
     */
    public String sanitizeText(String text) {
        if (!properties.isEnabled() || text == null) {
            return text;
        }

        String result = text;
        List<Pattern> patterns = properties.getCompiledPatterns();

        for (Pattern pattern : patterns) {
            result = sanitizeWithPattern(result, pattern);
        }

        return result;
    }

    /**
     * useregexexpressionpatternperformde-
     */
    private String sanitizeWithPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            // addmatchbeforeoftext
            result.append(text, lastEnd, matcher.start());
            // formatchofinallowperformsanitize
            String matched = matcher.group();
            result.append(maskValue(matched));
            lastEnd = matcher.end();
        }

        // addremainingtext
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    /**
     * forvalueperformmaskplace
     *
     * @param value original
     * @return sanitizeafterof
     */
    public String maskValue(String value) {
        if (value == null) {
            return null;
        }

        int length = value.length();
        String maskChar = properties.getMaskChar();
        int visibleChars = properties.getVisibleChars();
        int fullMaskThreshold = properties.getFullMaskThreshold();

        // shortvaluecompleteallcover
        if (length <= fullMaskThreshold) {
            return maskChar.repeat(Math.min(8, length));
        }

        // retainbeginning and endcanseecharacter
        int actualVisible = Math.min(visibleChars, length / 4);
        String prefix = value.substring(0, actualVisible);
        String suffix = value.substring(length - actualVisible);
        int maskLength = Math.min(8, length - 2 * actualVisible);

        return prefix + maskChar.repeat(maskLength) + suffix;
    }

    /**
     * createused forlogoutputofsecurityrequestheaderextractmust
     *
     * @param headerName  requestheadername
     * @param headerValue requestheader
     * @return formatizationofheaderinformation（alreadysanitize）
     */
    public String formatHeader(String headerName, String headerValue) {
        String sanitizedValue = sanitizeHeader(headerName, headerValue);
        return headerName + ": " + sanitizedValue;
    }

    /**
     * checkwhetherenablesanitize
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Authorization headerperformspecialplace
     * retainauthenticationtype（Bearer/Basic），sanitizecredentialpart
     *
     * @param authValue Authorization headerof
     * @return sanitizeafterof
     */
    public String sanitizeAuthorizationHeader(String authValue) {
        if (authValue == null) {
            return null;
        }

        // process Bearer token
        if (authValue.toLowerCase().startsWith("bearer ")) {
            String token = authValue.substring(7);
            return "Bearer " + maskValue(token);
        }

        // process Basic auth
        if (authValue.toLowerCase().startsWith("basic ")) {
            return "Basic " + maskChar(8);
        }

        // othertypedirectlysanitize
        return maskValue(authValue);
    }

    private String maskChar(int count) {
        return properties.getMaskChar().repeat(count);
    }
}
