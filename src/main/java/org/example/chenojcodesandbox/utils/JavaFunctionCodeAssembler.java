package org.example.chenojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import org.example.chenojcodesandbox.model.JudgeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将用户 Solution 代码与平台 Driver、JSON 运行时拼成可编译的 Main.java。
 */
public final class JavaFunctionCodeAssembler {

    /** 函数题常见集合/工具类，用户 Solution 未写 import 时自动补齐 */
    private static final String DEFAULT_USER_IMPORTS = "import java.util.*;\n";

    private static final String LIST_NODE_SOURCE =
            "class ListNode {\n"
                    + "    int val;\n"
                    + "    ListNode next;\n"
                    + "    ListNode() {}\n"
                    + "    ListNode(int val) { this.val = val; }\n"
                    + "    ListNode(int val, ListNode next) { this.val = val; this.next = next; }\n"
                    + "}\n";

    private static final String TREE_NODE_SOURCE =
            "class TreeNode {\n"
                    + "    int val;\n"
                    + "    TreeNode left;\n"
                    + "    TreeNode right;\n"
                    + "    TreeNode() {}\n"
                    + "    TreeNode(int val) { this.val = val; }\n"
                    + "    TreeNode(int val, TreeNode left, TreeNode right) {\n"
                    + "        this.val = val; this.left = left; this.right = right;\n"
                    + "    }\n"
                    + "}\n";

    private static final String FUNCTION_JSON_RUNTIME =
            "final class FunctionJudgeJson {\n"
                    + "    private FunctionJudgeJson() {}\n"
                    + "    static Object[] parseArgs(String json, String[] types) {\n"
                    + "        java.util.List<String> parts = splitTopLevelArray(json);\n"
                    + "        if (parts.size() != types.length) {\n"
                    + "            throw new IllegalArgumentException(\"参数个数与签名不一致\");\n"
                    + "        }\n"
                    + "        Object[] args = new Object[types.length];\n"
                    + "        for (int i = 0; i < types.length; i++) {\n"
                    + "            args[i] = parseValue(parts.get(i), types[i]);\n"
                    + "        }\n"
                    + "        return args;\n"
                    + "    }\n"
                    + "    static String formatResult(Object result, String returnType) {\n"
                    + "        if (result == null) return \"null\";\n"
                    + "        if (\"ListNode\".equals(returnType)) return listNodeToJson((ListNode) result);\n"
                    + "        if (\"int[]\".equals(returnType)) return intArrayToJson((int[]) result);\n"
                    + "        if (\"int[][]\".equals(returnType)) return int2DArrayToJson((int[][]) result);\n"
                    + "        if (\"String\".equals(returnType)) return quoteString((String) result);\n"
                    + "        if (\"boolean\".equals(returnType) || result instanceof Boolean) return String.valueOf(result);\n"
                    + "        if (\"long\".equals(returnType) || result instanceof Long) return String.valueOf(result);\n"
                    + "        return String.valueOf(result);\n"
                    + "    }\n"
                    + "    private static Object parseValue(String raw, String type) {\n"
                    + "        String s = raw == null ? \"\" : raw.trim();\n"
                    + "        if (\"int\".equals(type)) return Integer.parseInt(s);\n"
                    + "        if (\"long\".equals(type)) return Long.parseLong(s);\n"
                    + "        if (\"boolean\".equals(type)) return Boolean.parseBoolean(s);\n"
                    + "        if (\"String\".equals(type)) return parseStringLiteral(s);\n"
                    + "        if (\"int[]\".equals(type)) return parseIntArray(s);\n"
                    + "        if (\"int[][]\".equals(type)) return parseInt2DArray(s);\n"
                    + "        if (\"ListNode\".equals(type)) return parseListNode(s);\n"
                    + "        if (\"TreeNode\".equals(type)) return parseTreeNode(s);\n"
                    + "        throw new IllegalArgumentException(\"不支持的参数类型: \" + type);\n"
                    + "    }\n"
                    + "    private static TreeNode parseTreeNode(String s) {\n"
                    + "        s = s == null ? \"\" : s.trim();\n"
                    + "        if (s.startsWith(\"\\\"\") && s.endsWith(\"\\\"\")) {\n"
                    + "            s = parseStringLiteral(s);\n"
                    + "        }\n"
                    + "        if (s.isEmpty() || \"null\".equalsIgnoreCase(s) || \"#\".equals(s) || \"{}\".equals(s)) {\n"
                    + "            return null;\n"
                    + "        }\n"
                    + "        java.util.List<String> tokens;\n"
                    + "        if (s.startsWith(\"{\") && s.endsWith(\"}\")) {\n"
                    + "            tokens = splitTokens(s.substring(1, s.length() - 1));\n"
                    + "        } else if (s.startsWith(\"[\") && s.endsWith(\"]\")) {\n"
                    + "            tokens = splitTopLevelArray(s);\n"
                    + "        } else {\n"
                    + "            tokens = splitTokens(s);\n"
                    + "        }\n"
                    + "        return buildTreeFromStringTokens(tokens);\n"
                    + "    }\n"
                    + "    private static java.util.List<String> splitTokens(String inner) {\n"
                    + "        java.util.List<String> tokens = new java.util.ArrayList<>();\n"
                    + "        if (inner == null || inner.trim().isEmpty()) return tokens;\n"
                    + "        for (String part : inner.split(\",\")) {\n"
                    + "            tokens.add(part.trim());\n"
                    + "        }\n"
                    + "        return tokens;\n"
                    + "    }\n"
                    + "    private static boolean isNullTreeToken(String token) {\n"
                    + "        return token == null || token.isEmpty() || \"#\".equals(token) || \"null\".equalsIgnoreCase(token);\n"
                    + "    }\n"
                    + "    private static String normalizeTreeToken(String raw) {\n"
                    + "        if (raw == null) return null;\n"
                    + "        return parseStringLiteral(raw.trim());\n"
                    + "    }\n"
                    + "    private static TreeNode buildTreeFromStringTokens(java.util.List<String> tokens) {\n"
                    + "        if (tokens == null || tokens.isEmpty()) return null;\n"
                    + "        String rootToken = normalizeTreeToken(tokens.get(0));\n"
                    + "        if (isNullTreeToken(rootToken)) return null;\n"
                    + "        TreeNode root = new TreeNode(Integer.parseInt(rootToken));\n"
                    + "        java.util.Queue<TreeNode> queue = new java.util.LinkedList<>();\n"
                    + "        queue.offer(root);\n"
                    + "        int index = 1;\n"
                    + "        while (!queue.isEmpty() && index < tokens.size()) {\n"
                    + "            TreeNode current = queue.poll();\n"
                    + "            if (index < tokens.size()) {\n"
                    + "                String leftToken = normalizeTreeToken(tokens.get(index++));\n"
                    + "                if (!isNullTreeToken(leftToken)) {\n"
                    + "                    TreeNode left = new TreeNode(Integer.parseInt(leftToken));\n"
                    + "                    current.left = left;\n"
                    + "                    queue.offer(left);\n"
                    + "                }\n"
                    + "            }\n"
                    + "            if (index < tokens.size()) {\n"
                    + "                String rightToken = normalizeTreeToken(tokens.get(index++));\n"
                    + "                if (!isNullTreeToken(rightToken)) {\n"
                    + "                    TreeNode right = new TreeNode(Integer.parseInt(rightToken));\n"
                    + "                    current.right = right;\n"
                    + "                    queue.offer(right);\n"
                    + "                }\n"
                    + "            }\n"
                    + "        }\n"
                    + "        return root;\n"
                    + "    }\n"
                    + "    private static ListNode parseListNode(String s) {\n"
                    + "        int[] vals = parseIntArray(s);\n"
                    + "        if (vals.length == 0) return null;\n"
                    + "        ListNode head = new ListNode(vals[0]);\n"
                    + "        ListNode cur = head;\n"
                    + "        for (int i = 1; i < vals.length; i++) {\n"
                    + "            cur.next = new ListNode(vals[i]);\n"
                    + "            cur = cur.next;\n"
                    + "        }\n"
                    + "        return head;\n"
                    + "    }\n"
                    + "    private static int[] parseIntArray(String s) {\n"
                    + "        s = s.trim();\n"
                    + "        if (!s.startsWith(\"[\") || !s.endsWith(\"]\")) {\n"
                    + "            throw new IllegalArgumentException(\"期望 JSON 数组: \" + s);\n"
                    + "        }\n"
                    + "        String inner = s.substring(1, s.length() - 1).trim();\n"
                    + "        if (inner.isEmpty()) return new int[0];\n"
                    + "        java.util.List<String> items = splitTopLevelArray(\"[\" + inner + \"]\");\n"
                    + "        int[] arr = new int[items.size()];\n"
                    + "        for (int i = 0; i < items.size(); i++) arr[i] = Integer.parseInt(items.get(i).trim());\n"
                    + "        return arr;\n"
                    + "    }\n"
                    + "    private static int[][] parseInt2DArray(String s) {\n"
                    + "        s = s.trim();\n"
                    + "        if (!s.startsWith(\"[\") || !s.endsWith(\"]\")) {\n"
                    + "            throw new IllegalArgumentException(\"期望 JSON 二维数组: \" + s);\n"
                    + "        }\n"
                    + "        java.util.List<String> rows = splitTopLevelArray(s);\n"
                    + "        int[][] arr = new int[rows.size()][];\n"
                    + "        for (int i = 0; i < rows.size(); i++) {\n"
                    + "            arr[i] = parseIntArray(rows.get(i));\n"
                    + "        }\n"
                    + "        return arr;\n"
                    + "    }\n"
                    + "    private static String listNodeToJson(ListNode head) {\n"
                    + "        StringBuilder sb = new StringBuilder(\"[\");\n"
                    + "        ListNode cur = head;\n"
                    + "        boolean first = true;\n"
                    + "        while (cur != null) {\n"
                    + "            if (!first) sb.append(',');\n"
                    + "            sb.append(cur.val);\n"
                    + "            first = false;\n"
                    + "            cur = cur.next;\n"
                    + "        }\n"
                    + "        sb.append(']');\n"
                    + "        return sb.toString();\n"
                    + "    }\n"
                    + "    private static String intArrayToJson(int[] arr) {\n"
                    + "        StringBuilder sb = new StringBuilder(\"[\");\n"
                    + "        for (int i = 0; i < arr.length; i++) {\n"
                    + "            if (i > 0) sb.append(',');\n"
                    + "            sb.append(arr[i]);\n"
                    + "        }\n"
                    + "        sb.append(']');\n"
                    + "        return sb.toString();\n"
                    + "    }\n"
                    + "    private static String int2DArrayToJson(int[][] arr) {\n"
                    + "        StringBuilder sb = new StringBuilder(\"[\");\n"
                    + "        for (int i = 0; i < arr.length; i++) {\n"
                    + "            if (i > 0) sb.append(',');\n"
                    + "            sb.append(intArrayToJson(arr[i]));\n"
                    + "        }\n"
                    + "        sb.append(']');\n"
                    + "        return sb.toString();\n"
                    + "    }\n"
                    + "    private static String quoteString(String s) {\n"
                    + "        return \"\\\"\" + s.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\") + \"\\\"\";\n"
                    + "    }\n"
                    + "    private static String parseStringLiteral(String s) {\n"
                    + "        s = s.trim();\n"
                    + "        if (s.startsWith(\"\\\"\") && s.endsWith(\"\\\"\")) {\n"
                    + "            return s.substring(1, s.length() - 1).replace(\"\\\\\\\"\", \"\\\"\").replace(\"\\\\\\\\\", \"\\\\\");\n"
                    + "        }\n"
                    + "        return s;\n"
                    + "    }\n"
                    + "    private static java.util.List<String> splitTopLevelArray(String json) {\n"
                    + "        String s = json == null ? \"\" : json.trim();\n"
                    + "        if (!s.startsWith(\"[\") || !s.endsWith(\"]\")) {\n"
                    + "            throw new IllegalArgumentException(\"期望 JSON 数组\");\n"
                    + "        }\n"
                    + "        String inner = s.substring(1, s.length() - 1).trim();\n"
                    + "        java.util.List<String> out = new java.util.ArrayList<>();\n"
                    + "        if (inner.isEmpty()) return out;\n"
                    + "        int depth = 0;\n"
                    + "        int braceDepth = 0;\n"
                    + "        boolean inString = false;\n"
                    + "        boolean escape = false;\n"
                    + "        int start = 0;\n"
                    + "        for (int i = 0; i < inner.length(); i++) {\n"
                    + "            char c = inner.charAt(i);\n"
                    + "            if (escape) {\n"
                    + "                escape = false;\n"
                    + "                continue;\n"
                    + "            }\n"
                    + "            if (inString) {\n"
                    + "                if (c == '\\\\') escape = true;\n"
                    + "                else if (c == '\"') inString = false;\n"
                    + "                continue;\n"
                    + "            }\n"
                    + "            if (c == '\"') {\n"
                    + "                inString = true;\n"
                    + "                continue;\n"
                    + "            }\n"
                    + "            if (c == '[') depth++;\n"
                    + "            else if (c == ']') depth--;\n"
                    + "            else if (c == '{') braceDepth++;\n"
                    + "            else if (c == '}') braceDepth--;\n"
                    + "            else if (c == ',' && depth == 0 && braceDepth == 0) {\n"
                    + "                out.add(inner.substring(start, i).trim());\n"
                    + "                start = i + 1;\n"
                    + "            }\n"
                    + "        }\n"
                    + "        out.add(inner.substring(start).trim());\n"
                    + "        return out;\n"
                    + "    }\n"
                    + "}\n";

    private JavaFunctionCodeAssembler() {
    }

    public static String assemble(String userCode, JudgeConfig config) {
        if (config == null || StrUtil.isBlank(config.getMethodName())) {
            throw new IllegalArgumentException("须配置 methodName");
        }
        List<String> paramTypes = config.getParamTypes();
        if (paramTypes == null || paramTypes.isEmpty()) {
            throw new IllegalArgumentException("须配置 paramTypes");
        }
        paramTypes = paramTypes.stream()
                .map(JavaFunctionCodeAssembler::normalizeTypeName)
                .collect(Collectors.toList());
        String returnType = normalizeTypeName(StrUtil.blankToDefault(config.getReturnType(), "int"));
        NormalizedUserCode normalizedUser = normalizeFunctionUserCode(userCode);

        StringBuilder sb = new StringBuilder();
        // import 必须在任意 class 之前
        sb.append(mergeImports(normalizedUser.imports, normalizedUser.body));
        if (sb.length() > 0) {
            sb.append("\n");
        }
        // Driver 运行时引用 ListNode / TreeNode，须始终提供定义
        sb.append(LIST_NODE_SOURCE);
        sb.append(TREE_NODE_SOURCE);
        sb.append(normalizedUser.body).append("\n");
        sb.append(FUNCTION_JSON_RUNTIME);
        sb.append(buildMainClass(config.getMethodName(), paramTypes, returnType));
        return sb.toString();
    }

    private static final class NormalizedUserCode {
        final String imports;
        final String body;

        NormalizedUserCode(String imports, String body) {
            this.imports = imports;
            this.body = body;
        }
    }

    /**
     * 合并用户 import 与平台默认 import。用户未写 java.util 相关 import 且代码中用到集合类时，自动补 import java.util.*。
     */
    private static String mergeImports(String userImports, String userBody) {
        String imports = StrUtil.blankToDefault(userImports, "").trim();
        if (needsDefaultUtilImports(userBody, imports)) {
            if (imports.isEmpty()) {
                return DEFAULT_USER_IMPORTS.trim();
            }
            return imports + "\n" + DEFAULT_USER_IMPORTS.trim();
        }
        return imports;
    }

    private static boolean needsDefaultUtilImports(String userBody, String userImports) {
        if (StrUtil.isBlank(userBody)) {
            return false;
        }
        if (hasJavaUtilImport(userImports)) {
            return false;
        }
        // 常见未 import 的 java.util 类型（不含 TreeNode/ListNode，由平台提供）
        return userBody.matches("(?s).*\\b(List|ArrayList|Map|HashMap|Set|HashSet|Queue|Deque|Stack|PriorityQueue|LinkedList|Arrays|Collections)\\b.*");
    }

    private static boolean hasJavaUtilImport(String userImports) {
        if (StrUtil.isBlank(userImports)) {
            return false;
        }
        for (String line : userImports.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("import ")) {
                continue;
            }
            String target = trimmed.substring("import ".length()).replace(";", "").trim();
            if ("java.util.*".equals(target)) {
                return true;
            }
            if (target.startsWith("java.util.")) {
                return true;
            }
        }
        return false;
    }

    private static NormalizedUserCode normalizeFunctionUserCode(String userCode) {
        String code = userCode == null ? "" : userCode.trim();
        StringBuilder imports = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ") || trimmed.startsWith("import static ")) {
                imports.append(line).append("\n");
            } else {
                body.append(line).append("\n");
            }
        }
        String bodyStr = body.toString().trim();
        bodyStr = bodyStr.replace("public class Solution", "class Solution");
        bodyStr = bodyStr.replace("public  class Solution", "class Solution");
        if (!bodyStr.contains("class Solution")) {
            throw new IllegalArgumentException("函数题请实现 class Solution { ... }");
        }
        return new NormalizedUserCode(imports.toString().trim(), bodyStr);
    }

    private static String buildMainClass(String methodName, List<String> paramTypes, String returnType) {
        String typesLiteral = paramTypes.stream()
                .map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(", "));
        String classesLiteral = paramTypes.stream()
                .map(JavaFunctionCodeAssembler::toClassLiteral)
                .collect(Collectors.joining(", "));
        return "public class Main {\n"
                + "    private static final String METHOD_NAME = \"" + methodName + "\";\n"
                + "    private static final String RETURN_TYPE = \"" + returnType + "\";\n"
                + "    private static final String[] PARAM_TYPES = new String[]{" + typesLiteral + "};\n"
                + "    private static final Class<?>[] PARAM_CLASSES = new Class<?>[]{" + classesLiteral + "};\n"
                + "    public static void main(String[] args) throws Exception {\n"
                + "        java.io.BufferedReader reader = new java.io.BufferedReader(\n"
                + "                new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));\n"
                + "        String line = reader.readLine();\n"
                + "        if (line == null) line = \"\";\n"
                + "        Object[] invokeArgs = FunctionJudgeJson.parseArgs(line.trim(), PARAM_TYPES);\n"
                + "        Solution solution = new Solution();\n"
                + "        java.lang.reflect.Method method = Solution.class.getMethod(METHOD_NAME, PARAM_CLASSES);\n"
                + "        Object result = method.invoke(solution, invokeArgs);\n"
                + "        System.out.print(FunctionJudgeJson.formatResult(result, RETURN_TYPE));\n"
                + "    }\n"
                + "}\n";
    }

    private static String toClassLiteral(String type) {
        switch (normalizeTypeName(type)) {
            case "int":
                return "int.class";
            case "long":
                return "long.class";
            case "boolean":
                return "boolean.class";
            case "String":
                return "String.class";
            case "int[]":
                return "int[].class";
            case "int[][]":
                return "int[][].class";
            case "ListNode":
                return "ListNode.class";
            case "TreeNode":
                return "TreeNode.class";
            default:
                throw new IllegalArgumentException("不支持的类型: " + type);
        }
    }

    private static String normalizeTypeName(String type) {
        return StrUtil.blankToDefault(type, "").replaceAll("\\s+", "");
    }
}
