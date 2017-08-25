package ludwig.utils;

import ludwig.interpreter.ClassType;
import ludwig.model.*;
import org.apache.commons.lang.StringEscapeUtils;

import java.util.*;
import java.util.stream.Collectors;

public class NodeUtils {

    public static Object parseLiteral(String s) {
        switch (s) {
            case "true":
                return true;
            case "false":
                return false;
            case "null":
                return null;
            default:
                if (s.startsWith("'")) {
                    return StringEscapeUtils.unescapeJavaScript(s.substring(1, s.length() - 1));
                }
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException e1) {
                    return Double.parseDouble(s);
                }
        }
    }

    public static String formatLiteral(Object o) {
        if (o instanceof String) {
            return '\'' + StringEscapeUtils.escapeJavaScript(o.toString()) + '\'';
        }
        return String.valueOf(o);
    }

    public static List<Node> expandNode(Node node) {
        List<Node> nodes = new ArrayList<>();
        expandNode(node, true, nodes);
        return nodes;
    }

    private static void expandNode(Node<?> node, boolean onlyChildren, List<Node> nodes) {
        if (!onlyChildren) {
            nodes.add(node);
        }
        for (Node child : node.children()) {
            expandNode(child, false, nodes);
        }
    }

    public static String signature(Object obj) {
        if (obj instanceof Node) {
            Node<?> node = (Node) obj;
            if (node instanceof OverrideNode) {
                return signature(declaration((OverrideNode) node));
            }
            StringBuilder builder = new StringBuilder(node.toString());
            for (Node<?> child : node.children()) {
                if (child instanceof PlaceholderNode) {
                    builder.append(' ');
                    builder.append(((PlaceholderNode) child).getParameter());
                } else if (child instanceof VariableNode) {
                    builder.append(' ');
                    builder.append(child.toString());
                } else {
                    break;
                }
            }
            return builder.toString();
        }
        return obj.toString();
    }

    public static List<String> arguments(Node<?> node) {
        if (node instanceof ClassNode) {
            return ClassType.of((ClassNode) node).fields().stream().map(Object::toString).collect(Collectors.toList());
        }
        if (node instanceof VariableNode) {
            return Collections.singletonList("it");
        }
        if (node instanceof OverrideNode) {
            return arguments(declaration((OverrideNode) node));
        }
        List<String> args = new ArrayList<>();
        for (Node child: node.children()) {
            if (!(child instanceof VariableNode)) {
                 break;
            }
            args.add(((VariableNode) child).name());
        }
        return args;
    }

    public static FunctionNode declaration(OverrideNode node) {
        return (FunctionNode) ((ReferenceNode)node.children().get(0)).ref();
    }

    public static boolean isReadonly(Node<?> node) {
        return node == null || node.parentOfType(ProjectNode.class).isReadonly();
    }

    private static void collectLocals(Node<?> root, Node<?> stop, String filter, List<Node> locals) {
        if (root == stop) {
            return;
        }
        if (root instanceof VariableNode && ((VariableNode) root).name().startsWith(filter)) {
            locals.add(root);
        }
        root.children().forEach(child -> collectLocals(child, stop, filter, locals));
    }

    public static List<Node> collectLocals(Node<?> root, Node<?> stop, String filter) {
        List<Node> locals = new ArrayList<>();
        collectLocals(root, stop, filter, locals);
        locals.sort(Comparator.comparing(Object::toString));
        return locals;
    }

    public static boolean isField(Node node) {
        return (node instanceof VariableNode) && (node.parent() instanceof ClassNode);
    }

    public static int argumentsCount(Node<?> node) {
        return node.accept(new ArgumentsCount());
    }
}
