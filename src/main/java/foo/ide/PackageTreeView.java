package foo.ide;

import foo.model.*;
import foo.workspace.Workspace;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class PackageTreeView extends TreeView<NamedNode> {
    private final Workspace workspace;

    public PackageTreeView(Workspace workspace) {
        super(createRoot(workspace));
        this.workspace = workspace;
        setShowRoot(false);
    }

    private static TreeItem<NamedNode> createRoot(Workspace workspace) {
        TreeItem<NamedNode> root = new TreeItem<>();

        for (ProjectNode projectNode : workspace.getProjects()) {
            TreeItem<NamedNode> projectItem = new TreeItem<>(projectNode);

            root.getChildren().add(projectItem);

            for (Node packageNode : projectNode.children()) {
                processPackage(projectItem, (PackageNode) packageNode);
            }
        }

        return root;
    }

    private static void processPackage(TreeItem<NamedNode> parent, PackageNode packageNode) {
        TreeItem<NamedNode> packageItem = new TreeItem<>(packageNode);
        parent.getChildren().add(packageItem);

        for (Node node : packageNode.children()) {
            if (node instanceof PackageNode) {
                processPackage(packageItem, (PackageNode) node);
            }
        }
    }


}
