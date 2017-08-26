package ludwig.ide;

import com.sun.javafx.collections.ObservableListWrapper;
import com.sun.javafx.scene.control.skin.TextAreaSkin;
import impl.org.controlsfx.autocompletion.AutoCompletionTextFieldBinding;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import lombok.Getter;
import lombok.Setter;
import ludwig.changes.*;
import ludwig.interpreter.*;
import ludwig.model.*;
import ludwig.script.Lexer;
import ludwig.script.LexerException;
import ludwig.utils.NodeUtils;
import ludwig.utils.PrettyPrinter;
import ludwig.workspace.Environment;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static ludwig.utils.NodeUtils.arguments;
import static ludwig.utils.NodeUtils.declaration;

public class EditorPane extends SplitPane {
    private final Environment environment;
    private final Settings settings;
    private final ListView<Node> membersList = new ListView<>();
    private final PackageTreeView packageTree;
    private final GridPane signatureView = new GridPane();
    private final CheckBox lazyCheckbox = new CheckBox("Lazy");
    private final CodeEditor codeEditor;

    @Getter
    @Setter
    private EditorPane anotherPane;

    public EditorPane(Environment environment, Settings settings) {
        this.environment = environment;
        this.settings = settings;
        codeEditor = new CodeEditor(environment);

        packageTree = new PackageTreeView(environment.getWorkspace());
        packageTree.setPrefWidth(120);

        membersList.setMinWidth(120);

        packageTree.getSelectionModel().selectedItemProperty().addListener(observable -> fillMembers());

        membersList.setPrefHeight(1E6);


        TableColumn<NamedNode, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<NamedNode, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("comment"));
        descriptionColumn.setCellFactory(TextFieldTableCell.forTableColumn());

        codeEditor.setPrefHeight(1E6);


        MenuItem gotoDefinitionMenuItem = new MenuItem("Go to definition");

        gotoDefinitionMenuItem.setOnAction(e -> {
            Node sel = codeEditor.selectedNode();
            if (sel instanceof ReferenceNode) {
                gotoDefinition((ReferenceNode) sel);
            }
        });
        codeEditor.setContextMenu(new ContextMenu(gotoDefinitionMenuItem));



        membersList.getSelectionModel().selectedItemProperty().addListener(observable -> displayMember());

        membersList.setCellFactory(listView -> new SignatureListCell());

        getItems().addAll(packageTree, membersList, new VBox(signatureView, /*signatureToolbar,*/ lazyCheckbox, codeEditor));

        membersList.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2
                && membersList.getSelectionModel().selectedItemProperty().getValue() != null
                && anotherPane != null) {
                anotherPane.codeEditor.insertNode((Node<?>) membersList.getSelectionModel().selectedItemProperty().getValue());
            }
        });

        MenuItem addPackageMenuItem = new MenuItem("Add...", Icons.icon("add"));
        addPackageMenuItem.setOnAction(e -> addPackage());
        MenuItem deletePackageMenuItem = new MenuItem("Delete", null);
        deletePackageMenuItem.setOnAction(e -> deletePackage());
        packageTree.setContextMenu(new ContextMenu(addPackageMenuItem, deletePackageMenuItem));


        MenuItem addFunctionMenuItem = new MenuItem("Add Function...", Icons.icon("add"));
        addFunctionMenuItem.setOnAction(e -> addFunction());

        MenuItem overrideFunctionMenuItem = new MenuItem("Override...", Icons.icon("add"));
        overrideFunctionMenuItem.setOnAction(e -> overrideFunction());

        MenuItem runMenuItem = new MenuItem("Run...", Icons.icon("run"));
        runMenuItem.setOnAction(e -> runFunction());

        MenuItem deleteFunctionMenuItem = new MenuItem("Delete", null);
        deleteFunctionMenuItem.setOnAction(e -> deleteMember());

        membersList.setContextMenu(new ContextMenu(
            addFunctionMenuItem,
            overrideFunctionMenuItem,
            runMenuItem,
            deleteFunctionMenuItem
        ));

        environment.getWorkspace().changeListeners().add(this::processChanges);
    }

    private void deleteMember() {
        if (isReadonly()) {
            return;
        }
        Node selectedItem = selectedMember();
        if (selectedItem != null) {
            environment.getWorkspace().apply(singletonList(new Delete().id(selectedItem.id())));
        }
    }

    private void overrideFunction() {
        TreeItem<NamedNode> selectedItem = packageTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() instanceof PackageNode) {
            PackageNode packageNode = (PackageNode) selectedItem.getValue();

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Override");
            dialog.setHeaderText("");

            AutoCompletionTextFieldBinding<Node> autoCompletionTextFieldBinding =
                new AutoCompletionTextFieldBinding<>(
                    dialog.getEditor(),
                    param -> {
                        List<Node> suggestions = new ArrayList<>();
                        suggestions.addAll(environment.getSymbolRegistry().symbols(param.getUserText()));

                        return suggestions;
                    },
                    new NodeStringConverter());

            autoCompletionTextFieldBinding.setVisibleRowCount(20);
            Node[] ref = {null};
            autoCompletionTextFieldBinding.setOnAutoCompleted(e -> {
                ref[0] = e.getCompletion();
            });


            dialog.showAndWait().ifPresent(signature -> {
                if (ref[0] != null) {
                    List<Change> changes = new ArrayList<>();

                    InsertNode insertOverride = new InsertNode()
                        .node(new OverrideNode().id(Change.newId()))
                        .parent(packageNode.id());

                    changes.add(insertOverride);

                    changes.add(new InsertReference()
                        .ref(ref[0].id())
                        .id(Change.newId())
                        .parent(insertOverride.node().id()));

                    environment.getWorkspace().apply(changes);

                    OverrideNode o = environment.getWorkspace().node(insertOverride.node().id());
                    navigateTo(o);
                }
            });
        }
    }

    private void addPackage() {
        if (isReadonly()) {
            return;
        }
        TreeItem<NamedNode> selectedItem = packageTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            Node parent = selectedItem.getValue();
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add a package");
            dialog.setHeaderText("");
            dialog.setContentText("Package name");

            dialog.showAndWait().ifPresent(name -> {
                InsertNode insert = new InsertNode()
                    .node(new PackageNode().name(name).id(Change.newId()))
                    .parent(parent.id());
                environment.getWorkspace().apply(singletonList(insert));
                navigateTo(environment.getWorkspace().node(insert.node().id()));
            });
        }
    }

    private void deletePackage() {
        if (isReadonly()) {
            return;
        }
        TreeItem<NamedNode> selectedItem = packageTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            Node packageNode = selectedItem.getValue();
            environment.getWorkspace().apply(singletonList(new Delete().id(packageNode.id())));
        }
    }


    private void runFunction() {
        Node fn = selectedMember();
        if (!(fn instanceof NamedNode)) {
            return;
        }
        try {
            Callable callable = (fn instanceof Callable) ? (Callable) fn : new CallableRef(fn);
            Object result;
            if (callable.argCount() > 0) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Execute function");
                dialog.setHeaderText("Enter function arguments");
                dialog.setContentText(((NamedNode) fn).name());

                Optional<String> params = dialog.showAndWait();
                if (params.isPresent()) {
                    Object[] args = Lexer.read(new StringReader(params.get()))
                        .stream()
                        .filter(s -> !s.equals("(") && !s.equals(")"))
                        .map(NodeUtils::parseLiteral)
                        .map(x -> callable.isLazy() ? (Delayed<?>) () -> x : x)
                        .toArray();
                    result = callable.call(args);
                } else {
                    return;
                }

            } else {
                result = callable.call();
            }
            new Alert(Alert.AlertType.INFORMATION, "Result: " + NodeUtils.formatLiteral(result)).show();
        } catch (Exception err) {
            err.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error: " + err.toString()).show();
        }
    }

    private void addFunction() {
        if (isReadonly()) {
            return;
        }
        TreeItem<NamedNode> selectedItem = packageTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() instanceof PackageNode) {
            PackageNode packageNode = (PackageNode) selectedItem.getValue();

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Add a function");
            dialog.setHeaderText("Enter function signature");

            dialog.showAndWait().ifPresent(signature -> {
                List<String> parts = Collections.emptyList();
                try {
                    parts = Lexer.read(new StringReader(signature))
                        .stream()
                        .filter(s -> !s.equals("(") && !s.equals(")"))
                        .collect(Collectors.toList());
                } catch (IOException | LexerException t) {
                }
                if (!parts.isEmpty()) {
                    List<Change> changes = new ArrayList<>();

                    InsertNode insertFn = new InsertNode()
                        .node(new FunctionNode().name(parts.get(0)).id(Change.newId()))
                        .parent(packageNode.id());

                    changes.add(insertFn);

                    String prev = null;
                    for (int i = 1; i < parts.size(); i++) {
                        String id = Change.newId();
                        changes.add(new InsertNode()
                            .node(new VariableNode().name(parts.get(i)).id(id))
                            .parent(insertFn.node().id())
                            .prev(prev));
                        prev = id;
                    }

                    environment.getWorkspace().apply(changes);

                    FunctionNode fn = environment.getWorkspace().node(insertFn.node().id());
                    navigateTo(fn);
                }

            });
        }
    }

    private void displayMember() {
        Node sel = selectedMember();
        signatureView.getChildren().clear();
        codeEditor.setContent(null);

        if (sel == null) {
            return;
        }

        NamedNode decl = (sel instanceof OverrideNode) ? declaration((OverrideNode) sel) : (NamedNode) sel;

        signatureView.add(new Label("Name"), 1, 1);
        signatureView.add(new Label("Description"), 2, 1);
        signatureView.add(nameTextField(decl), 1, 2);
        signatureView.add(commentTextField(decl), 2, 2);

        if (decl instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) decl;
            int row = 3;
            for (Node n : fn.children()) {
                if (!(n instanceof VariableNode)) {
                    break;
                }
                signatureView.add(nameTextField((VariableNode) n), 1, row);
                signatureView.add(commentTextField(n), 2, row);
                row++;
            }
            lazyCheckbox.setSelected(fn.isLazy());
        }
        codeEditor.setContent(sel);
    }

    private Node selectedMember() {
        if (membersList.getSelectionModel() == null) {
            return null;
        }
        return membersList.getSelectionModel().getSelectedItem();
    }


    private void processChanges(Change change) {
        if (!environment.getWorkspace().isBatchUpdate()) {
            refresh();
        }
    }

    private void gotoDefinition(ReferenceNode node) {
        navigateTo(node.ref());
    }

    private void navigateTo(Node<?> node) {
        packageTree.select(node.parentOfType(PackageNode.class));
        FunctionNode fn = node.parentOfType(FunctionNode.class);
        if (fn != null) {
            membersList.getSelectionModel().select(fn);
            Node decl = node.parentOfType(AssignmentNode.class);
            if (decl == null) {
                decl = node.parentOfType(ForNode.class);
            }
            if (decl == null) {
                decl = node.parentOfType(LambdaNode.class);
            }
            if (decl != null) {
                codeEditor.locate(decl);
            }
        }
    }

    private void fillMembers() {
        membersList.getItems().clear();
        TreeItem<NamedNode> selectedItem = packageTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            NamedNode node = selectedItem.getValue();

            if (node instanceof PackageNode) {
                PackageNode packageNode = (PackageNode) node;
                membersList.setItems(new ObservableListWrapper<>(packageNode.children()
                    .stream()
                    .filter(item -> !(item instanceof PackageNode))
                    .map(item -> (Node<?>) item)
                    .sorted(Comparator.comparing(NodeUtils::signature))
                    .collect(Collectors.toList())));
            }

            if (!membersList.getItems().isEmpty()) {
                membersList.getSelectionModel().select(0);
            }
        }
    }

    private TextField commentTextField(Node<?> node) {
        return new TextField(node.comment()) {
            private String saved;

            {
                setOnAction(e -> applyChanges());

                this.focusedProperty().addListener(e -> {
                    if (focusedProperty().get()) {
                        saved = getText();
                    } else {
                        if (!Objects.equals(getText(), saved)) {
                            applyChanges();
                        }
                    }
                });

                setEditable(!isReadonly());
                setPrefWidth(1000);
            }

            private void applyChanges() {
                environment.getWorkspace().apply(singletonList(new Comment().nodeId(node.id()).comment(getText())));
            }
        };
    }

    private TextField nameTextField(NamedNode node) {
        return new TextField(node.name()) {
            private String saved;

            {
                setOnAction(e -> applyChanges());

                this.focusedProperty().addListener(e -> {
                    if (focusedProperty().get()) {
                        saved = getText();
                    } else {
                        if (!getText().equals(saved)) {
                            applyChanges();
                        }
                    }
                });

                setEditable(!isReadonly());
                setMinWidth(100);
            }

            private void applyChanges() {
                environment.getWorkspace().apply(singletonList(new Rename().setNodeId(node.id()).name(getText())));
            }
        };
    }

    private void refresh() {
        Node packageSelection = null;
        Node memberSelection = null;
        NamedNode signatureSelection = null;
        Node codeSelection = null;

        TreeItem<NamedNode> selectedItem = packageTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            packageSelection = selectedItem.getValue();
        }

        memberSelection = selectedMember();

        codeSelection = codeEditor.selectedNode();

        packageTree.refresh();
        packageTree.getSelectionModel().clearSelection();
        membersList.getSelectionModel().clearSelection();

        displayMember();

        if (memberSelection != null) {
            navigateTo(memberSelection);
        } else if (packageSelection != null) {
            navigateTo(packageSelection);
        }

        if (codeSelection != null) {
            codeEditor.locate(codeSelection);
        }
    }



    private boolean isReadonly() {
        return packageTree.getSelectionModel().getSelectedItem() == null || NodeUtils.isReadonly(packageTree.getSelectionModel().getSelectedItem().getValue());
    }
}
