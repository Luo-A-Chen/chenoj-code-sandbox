package org.example.chenojcodesandbox.utils;

import org.example.chenojcodesandbox.model.JudgeConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFunctionCodeAssemblerTest {

    @Test
    void assembleContainsDriverAndSolution() {
        JudgeConfig config = new JudgeConfig();
        config.setMethodName("add");
        config.setParamTypes(Arrays.asList("int", "int"));
        config.setReturnType("int");

        String user = "class Solution { public int add(int a, int b) { return a + b; } }";
        String full = JavaFunctionCodeAssembler.assemble(user, config);

        assertTrue(full.contains("class Solution"));
        assertTrue(full.contains("public class Main"));
        assertTrue(full.contains("METHOD_NAME = \"add\""));
        assertTrue(full.contains("FunctionJudgeJson"));
    }

    @Test
    void assembleSupportsInt2DArrayParam() {
        JudgeConfig config = new JudgeConfig();
        config.setMethodName("Find");
        config.setParamTypes(Arrays.asList("int", "int[][]"));
        config.setReturnType("boolean");

        String user = "class Solution { public boolean Find(int target, int[][] array) { return false; } }";
        String full = JavaFunctionCodeAssembler.assemble(user, config);

        assertTrue(full.contains("\"int[][]\""));
        assertTrue(full.contains("int[][].class"));
        assertTrue(full.contains("parseInt2DArray"));
    }

    @Test
    void assembleHoistsUserImportsBeforeListNode() {
        JudgeConfig config = new JudgeConfig();
        config.setMethodName("Merge");
        config.setParamTypes(Arrays.asList("ListNode", "ListNode"));
        config.setReturnType("ListNode");

        String user = "import java.util.ArrayList;\n"
                + "public class Solution {\n"
                + "  public ListNode Merge(ListNode a, ListNode b) { return a; }\n"
                + "}";
        String full = JavaFunctionCodeAssembler.assemble(user, config);

        int importIdx = full.indexOf("import java.util.ArrayList");
        int listNodeIdx = full.indexOf("class ListNode");
        assertTrue(importIdx >= 0);
        assertTrue(listNodeIdx >= 0);
        assertTrue(importIdx < listNodeIdx);
    }

    @Test
    void assembleSplitsQuotedTreeInputAsSingleArg() {
        JudgeConfig config = new JudgeConfig();
        config.setMethodName("preorderTraversal");
        config.setParamTypes(Arrays.asList("TreeNode"));
        config.setReturnType("int[]");

        String user = "class Solution { public int[] preorderTraversal(TreeNode root) { return new int[0]; } }";
        String full = JavaFunctionCodeAssembler.assemble(user, config);

        assertTrue(full.contains("PARAM_TYPES = new String[]{\"TreeNode\"}"));
        assertTrue(full.contains("boolean inString = false"));
        assertTrue(full.contains("java.util.Queue<TreeNode> queue = new java.util.LinkedList<>()"));
        assertTrue(!full.contains("2 * i + 1"));
    }

    @Test
    void assembledSourceHasNoDuplicateTreeHelpers() {
        JudgeConfig config = new JudgeConfig();
        config.setMethodName("Merge");
        config.setParamTypes(Arrays.asList("ListNode", "ListNode"));
        config.setReturnType("ListNode");

        String user = "class Solution { public ListNode Merge(ListNode a, ListNode b) { return a; } }";
        String full = JavaFunctionCodeAssembler.assemble(user, config);

        assertTrue(full.indexOf("parseTreeNode(String)") == full.lastIndexOf("parseTreeNode(String)"));
        assertTrue(!full.contains("buildTreeFromLevelOrder(java.util.List<Integer>"));
    }

    @Test
    void assembleInjectsUtilImportsWhenSolutionUsesListWithoutImport() {
        JudgeConfig config = new JudgeConfig();
        config.setMethodName("postorderTraversal");
        config.setParamTypes(Arrays.asList("TreeNode"));
        config.setReturnType("int[]");

        String user = "class Solution {\n"
                + "  public int[] postorderTraversal(TreeNode root) {\n"
                + "    List<Integer> list = new ArrayList<>();\n"
                + "    postDFS(root, list);\n"
                + "    return list.stream().mapToInt(i -> i).toArray();\n"
                + "  }\n"
                + "  void postDFS(TreeNode root, List<Integer> list) {\n"
                + "    if (root == null) return;\n"
                + "    postDFS(root.left, list);\n"
                + "    postDFS(root.right, list);\n"
                + "    list.add(root.val);\n"
                + "  }\n"
                + "}";
        String full = JavaFunctionCodeAssembler.assemble(user, config);

        int importIdx = full.indexOf("import java.util.*");
        int listNodeIdx = full.indexOf("class ListNode");
        assertTrue(importIdx >= 0);
        assertTrue(importIdx < listNodeIdx);
    }

    @Test
    void assembleDoesNotDuplicateUtilImportsWhenUserAlreadyImported() {
        JudgeConfig config = new JudgeConfig();
        config.setMethodName("postorderTraversal");
        config.setParamTypes(Arrays.asList("TreeNode"));
        config.setReturnType("int[]");

        String user = "import java.util.ArrayList;\n"
                + "import java.util.List;\n"
                + "class Solution {\n"
                + "  public int[] postorderTraversal(TreeNode root) {\n"
                + "    List<Integer> list = new ArrayList<>();\n"
                + "    return list.stream().mapToInt(i -> i).toArray();\n"
                + "  }\n"
                + "}";
        String full = JavaFunctionCodeAssembler.assemble(user, config);

        assertTrue(full.indexOf("import java.util.*") == full.lastIndexOf("import java.util.*"));
        assertTrue(full.contains("import java.util.ArrayList"));
        assertTrue(full.contains("import java.util.List"));
    }
}
