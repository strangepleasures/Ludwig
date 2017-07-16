package foo.utils;

import foo.model.*;

public class PrintUtil {
    public static String toString(Node node) {
        PrintVisitor visitor = new PrintVisitor();
        node.accept(visitor);
        return visitor.toString();
    }

    private static class PrintVisitor implements NodeVisitor<Void> {
        private final StringBuilder out = new StringBuilder();
        private int indentation;
        private boolean inline;

        @Override
        public Void visitBoundCall(BoundCallNode boundCallNode) {
            indent();
            out.append(boundCallNode.getFunction().getName()).append("\n");
            indentation++;
            for (ParameterNode param : boundCallNode.getFunction().getParameters()) {
                indent();
                out.append(param.getName()).append(": ");
                if (boundCallNode.getArguments().containsKey(param)) {
                    inline = true;
                    indentation++;
                    boundCallNode.getArguments().get(param).accept(this);
                    indentation--;
                } else {
                    out.append('\n');
                }
            }
            indentation--;
            return null;
        }

        @Override
        public Void visitFunction(FunctionNode functionNode) {
            indent();
            out.append("def ").append(functionNode.getName()).append(" [");
            boolean first = true;
            for (ParameterNode param : functionNode.getParameters()) {
                if (!first) {
                    out.append(' ');
                }
                first = false;
                out.append(param.getName());
            }
            out.append("]\n");
            indentation++;
            for (Node node : functionNode.getNodes()) {
                node.accept(this);
            }
            indentation--;
            return null;
        }

        @Override
        public Void visitLet(LetNode letNode) {
            indent();
            out.append("= ").append(letNode.getName()).append(' ');
            inline = true;
            letNode.getValue().accept(this);
            return null;
        }

        @Override
        public Void visitList(ListNode listNode) {
            indent();
            out.append("list\n");
            indentation++;
            for (Node item : listNode.getItems()) {
                item.accept(this);
            }
            indentation--;
            return null;
        }

        @Override
        public Void visitLiteral(LiteralNode literalNode) {
            indent();
            out.append(literalNode.getText()).append('\n');
            return null;
        }

        @Override
        public Void visitPackage(PackageNode packageNode) {
            indent();
            out.append("package ").append(packageNode.getName()).append('\n');
            indentation++;
            for (Node item : packageNode.getItems()) {
                item.accept(this);
            }
            indentation--;
            return null;
        }

        @Override
        public Void visitParameter(ParameterNode parameterNode) {
            return null;
        }

        @Override
        public Void visitRef(RefNode refNode) {
            indent();
            out.append(refNode.getNode().getName()).append('\n');
            return null;
        }

        @Override
        public Void visitUnboundCall(UnboundCallNode unboundCallNode) {
            indent();
            if (unboundCallNode.getNodes().get(0) instanceof RefNode) {
                out.append(((RefNode) unboundCallNode.getNodes().get(0)).getNode().getName());
                if (unboundCallNode.getNodes().size() == 1) {
                    out.append(" []\n");
                } else {
                    out.append('\n');
                    indentation++;
                    unboundCallNode.getNodes()
                        .stream()
                        .skip(1)
                        .forEach(item -> item.accept(this));
                    indentation--;
                }
            } else {
                // TODO: Support ?
            }
            return null;
        }

        @Override
        public Void visitLambda(LambdaNode lambdaNode) {
            indent();
            out.append("lambda [");
            boolean first = true;
            for (ParameterNode param : lambdaNode.getParameters()) {
                if (!first) {
                    out.append(' ');
                }
                first = false;
                out.append(param.getName());
            }
            out.append("]\n");
            indentation++;
            for (Node node : lambdaNode.getItems()) {
                node.accept(this);
            }
            indentation--;
            return null;
        }

        @Override
        public Void visitReturn(ReturnNode returnNode) {
            indent();
            out.append("return");
            if (returnNode.getValue() != null) {
                inline = true;
                out.append(' ');
                returnNode.getValue().accept(this);
            } else {
                out.append('\n');
            }
            return null;
        }

        @Override
        public Void visitProject(ProjectNode projectNode) {
            indent();
            out.append("project ").append(projectNode.getName()).append('\n');
            indentation++;
            for (Node item : projectNode.getPackages()) {
                item.accept(this);
            }
            indentation--;
            return null;
        }

        @Override
        public Void visitIf(IfNode ifNode) {
            indent();
            out.append("if\n");
            indentation++;
            for (Node item : ifNode.getNodes()) {
                item.accept(this);
            }
            indentation--;
            return null;
        }

        @Override
        public Void visitAnd(AndNode andNode) {
            // TODO:
            return null;
        }

        @Override
        public Void visitOr(OrNode orNode) {
            // TODO:
            return null;
        }

        private void indent() {
            if (!inline) {
                for (int i = 0; i < indentation; i++) {
                    out.append("  ");
                }
            }
            inline = false;
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }
}
