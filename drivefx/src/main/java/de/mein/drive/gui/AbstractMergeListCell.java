package de.mein.drive.gui;

import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.EmptyRowConflict;
import de.mein.drive.sql.Stage;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Callback;

/**
 * Created by xor on 5/30/17.
 */
@SuppressWarnings("Duplicates")
public abstract class AbstractMergeListCell extends ListCell<Conflict> {
    protected HBox hbox = new HBox();
    protected HBox spacer = new HBox();
    protected HBox indentSpacer = new HBox();
    protected Button button = new Button("x");
    protected VBox vBox = new VBox();
    protected Label label = new Label("..not set..");
    protected Label lblHash = new Label("-");
    protected Conflict lastSelected;
    protected int indent = 0;
    // left to right
    protected boolean buttonOnRight = true;

    public AbstractMergeListCell() {
        super();

        init();
        indent();
        setGraphic(hbox);
        label.setMouseTransparent(true);
        lblHash.setMouseTransparent(true);
        spacer.setMouseTransparent(true);
        indentSpacer.setMouseTransparent(true);
        button.setMouseTransparent(false);
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                handleAction(event);
            }
        });
    }

    void indent() {
        vBox.prefHeightProperty().bind(button.heightProperty()
                .subtract(button.paddingProperty().getValue().getTop())
                .subtract(button.paddingProperty().getValue().getBottom()));
        indentSpacer.prefWidthProperty().setValue(indent);
        HBox.setHgrow(spacer, Priority.ALWAYS);
    }

    protected void addChildren(Pane box, Node... elements) {
        for (Node e : elements)
            box.getChildren().add(e);
    }

    abstract Stage getConflictSide(Conflict dependsOn);

    protected static Background BACKGROUND_WHITE = new Background(new BackgroundFill(new Color(.3, .3, .3, 1), CornerRadii.EMPTY, Insets.EMPTY));

    @Override
    protected void updateItem(Conflict conflict, boolean empty) {
        super.updateItem(conflict, empty);
        if (conflict instanceof EmptyRowConflict) {
            setBackground(BACKGROUND_WHITE);
        } else {
            indent = 0;
            if (empty || conflict == null) {
                setGraphic(null);
                setBackground(null);
                lastSelected = null;
            } else {
                lastSelected = conflict;
                boolean parentDeleted = false;
                Stage side = getConflictSide(conflict);
                Conflict dependsOn = conflict.getDependsOn();
                while (dependsOn != null) {
                    indent += 10;
                    Stage dependsOnSide = getConflictSide(dependsOn);
                    if (dependsOnSide != null && dependsOnSide.getDeleted())
                        parentDeleted = true;
                    dependsOn = dependsOn.getDependsOn();
                }
                if (conflict.isLeft() && !isLeft() || conflict.isRight() && isLeft() || !conflict.hasDecision()) {
                    if (side != null) {
                        label.setText(side.getName());
                        lblHash.setText(side.getContentHash());
                    }
                    else if (parentDeleted) {
                        label.setText("<parent deleted>");
                        lblHash.setText("-");
                    }
                    else
                        System.err.println("j9034n3of");
                    setGraphic(hbox);
                } else {
                    setGraphic(null);
                }
                // olde
                if (isSelected()) {
                    setBackground(createSelectedBackground());
                } else if (parentDeleted || (side != null && side.getDeleted())) {
                    setBackground(createDeletedBackground());
                } else {
                    setBackground(createDefaultdBackground());
                }
            }
            indent();
        }
    }


    abstract boolean isLeft();

    private Background createSelectedBackground() {
        return new Background(new BackgroundFill(new Color(.5, .5, 1, 1), CornerRadii.EMPTY, Insets.EMPTY));
    }


    abstract void handleAction(ActionEvent event);

    protected Background createDeletedBackground() {
        return new Background(new BackgroundFill(new Color(1, .7, .7, 1), CornerRadii.EMPTY, Insets.EMPTY));
    }

    protected Background createDefaultdBackground() {
        return new Background(new BackgroundFill(new Color(.7, 1, .7, 1), CornerRadii.EMPTY, Insets.EMPTY));
    }

    /**
     * add your stuff to the hbox here
     */
    abstract void init();

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createMergeCellFactory(ListView<Conflict> leftList, ListView<Conflict> rightList) {
        Callback<ListView<Conflict>, ListCell<Conflict>> mergeCellFactory = param -> new MergeListCell(leftList, rightList);
        return mergeCellFactory;
    }

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createLeftCellFactory(ListView<Conflict> mergeList, ListView<Conflict> rightList) {
        Callback<ListView<Conflict>, ListCell<Conflict>> leftCellFactory = param -> new LeftMergeListCell(mergeList, rightList);
        return leftCellFactory;
    }

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createRightCellFactory(ListView<Conflict> leftList, ListView<Conflict> mergeList) {
        Callback<ListView<Conflict>, ListCell<Conflict>> rightCellFactory = param -> new RightMergeListCell(leftList, mergeList);
        return rightCellFactory;
    }

    public static void setup(ListView<Conflict> listLeft, ListView<Conflict> listMerge, ListView<Conflict> listRight) {
        listLeft.setCellFactory(createLeftCellFactory(listMerge, listRight));
        listMerge.setCellFactory(createMergeCellFactory(listLeft, listRight));
        listRight.setCellFactory(createRightCellFactory(listLeft, listMerge));
        bindSelections(listLeft, listMerge, listRight);
        listRight.setItems(listLeft.getItems());
        listMerge.setItems(listLeft.getItems());
    }

    public static class AAAAA {
        public static void main(String[] args) {
            System.out.println(new JFXPanel());
            System.out.println("AbstractMergeListCell.main");
            Platform.runLater(() -> N.r(() -> {
                javafx.stage.Stage stage = new javafx.stage.Stage();
                HBox root = new HBox();
                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.show();

                ListView<Conflict> mergeList = new ListView<>();
                Conflict c1 = new Conflict(null, new Stage().setName("a1").setId(1L), new Stage().setName("b1").setId(1L));
                Conflict c2 = new Conflict(null, new Stage().setName("a2").setId(2L), new Stage().setName("b2").setId(2L)).chooseRight();
                Conflict c3 = new Conflict(null, new Stage().setName("a3").setId(3L), new Stage().setName("b3").setId(3L)).chooseLeft();
                ListView<Conflict> rightList = new ListView<>(mergeList.getItems());
                ListView<Conflict> leftList = new ListView<>(mergeList.getItems());

                mergeList.setCellFactory(createMergeCellFactory(leftList, rightList));

                mergeList.getItems().addAll(c1, c2, c3);

                leftList.setCellFactory(createLeftCellFactory(mergeList, rightList));
                rightList.setCellFactory(createRightCellFactory(leftList, mergeList));
                root.getChildren().addAll(leftList, mergeList, rightList);


                //bindSelections(leftList, mergeList, rightList);

            }));

            new WaitLock().lock().lock();

        }
    }

    public static void bindSelections(ListView a, ListView b, ListView c) {
        a.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                selectSame(a, b, c);
            }
        });
        b.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null)
                selectSame(b, a, c);
        });
        c.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null)
                selectSame(c, a, b);
        });

    }

    public static void selectSame(ListView source, ListView other1, ListView other2) {
        other1.selectionModelProperty().setValue(source.getSelectionModel());
        other2.selectionModelProperty().setValue(source.getSelectionModel());
    }

}
