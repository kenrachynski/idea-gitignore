/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package mobi.hsz.idea.gitignore.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Util class to speed up and limit regex operation on the files paths.
 *
 * @author Jakub Chrzanowski <jakub@hsz.mobi>
 * @since 1.3.1
 */
public class MatcherUtil {
    /**
     * Extracts alphanumeric parts from the regex pattern and checks if any of them is contained in the tested path.
     * Looking for the parts speed ups the matching and prevents from running whole regex on the string.
     *
     * @param matcher to explode
     * @param path to check
     * @return path matches the pattern
     */
    public static boolean match(@Nullable Matcher matcher, @Nullable String path) {
        if (matcher == null || path == null) {
            return false;
        }

        String[] parts = getParts(matcher);
        if (!matchAllParts(parts, path)) {
            return false;
        }

        try {
            return matcher.reset(path).matches();
        } catch (StringIndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * Checks if given path contains all of the path parts.
     *
     * @param parts that should be contained in path
     * @param path to check
     * @return path contains all parts
     */
    public static boolean matchAllParts(@Nullable String[] parts, @Nullable String path) {
        if (parts == null || path == null) {
            return false;
        }

        int index = -1;
        for (String part : parts) {
            index = path.indexOf(part, index);
            if (index == -1) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if given path contains any of the path parts
     * .
     * @param parts that should be contained in path
     * @param path to check
     * @return path contains any of the parts
     */
    public static boolean matchAnyPart(@Nullable String[] parts, @Nullable String path) {
        if (parts == null || path == null) {
            return false;
        }

        for (String part : parts) {
            if (path.contains(part)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts alphanumeric parts from {@link Matcher} pattern.
     *
     * @param matcher to handle
     * @return extracted parts
     */
    @NotNull
    public static String[] getParts(@Nullable Matcher matcher) {
        if (matcher == null) {
            return new String[0];
        }
        return getParts(matcher.pattern());
    }

    /**
     * Extracts alphanumeric parts from  {@link Pattern}.
     *
     * @param pattern to handle
     * @return extracted parts
     */
    @NotNull
    public static String[] getParts(@Nullable Pattern pattern) {
        if (pattern == null) {
            return new String[0];
        }

        final List<String> parts = ContainerUtil.newArrayList();
        final String sPattern = pattern.toString();

        String part = "";
        for (int i = 0; i < sPattern.length(); i++) {
            if (Character.isLetterOrDigit(sPattern.charAt(i))) {
                part += sPattern.charAt(i);
            } else if (!part.isEmpty()) {
                parts.add(part);
                part = "";
            }
        }

        return parts.toArray(new String[parts.size()]);
    }
}
