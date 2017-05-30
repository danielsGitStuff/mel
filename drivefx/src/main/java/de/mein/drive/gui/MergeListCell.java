package de.mein.drive.gui;

import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.drive.service.sync.Conflict;
import de.mein.drive.sql.Stage;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;

/**
 * Created by xor on 5/30/17.
 */
@SuppressWarnings("Duplicates")
public abstract class MergeListCell extends ListCell<Conflict> {
    HBox hbox = new HBox();
    Label label = new Label("(empty)");
    Pane pane = new Pane();
    Button button = new Button("x");
    Conflict lastSelected;
    // left to right
    protected boolean buttonOnRight = true;

    public MergeListCell() {
        super();
        hbox.getChildren().addAll(label, pane, button);
        HBox.setHgrow(pane, Priority.ALWAYS);
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                handleAction(event);
            }
        });
    }

    abstract void handleAction(ActionEvent event);

    abstract void init();

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createMergeCellFactory() {
        Callback<ListView<Conflict>, ListCell<Conflict>> mergeCellFactory = param -> new MergeListCell() {


            @Override
            void handleAction(ActionEvent event) {
                button.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent event) {
                        if (lastSelected != null) {
                            System.out.println("MergeListCell.unsolve " + lastSelected);
                            lastSelected.chooseNothing();
                            getListView().refresh();
                        }

                    }
                });
            }

            @Override
            void init() {

            }

            @Override
            protected void updateItem(Conflict conflict, boolean empty) {
                super.updateItem(conflict, empty);
                setText(null);  // No text in label of super class
                if (empty || conflict == null) {
                    setGraphic(null);
                    lastSelected = null;
                } else {
                    lastSelected = conflict;
                    if (!conflict.hasDecision()) {
                        hbox.getChildren().clear();
                        label.setText("???");
                        hbox.getChildren().addAll(new Pane(), label, new Pane());
                        setTextAlignment(TextAlignment.RIGHT);
                    } else {
                        if (conflict.isRight()) {
                            if (!buttonOnRight) {
                                hbox.getChildren().clear();
                                hbox.getChildren().addAll(label, pane, button);
                                buttonOnRight = true;
                            }
                        } else {
                            if (buttonOnRight) {
                                hbox.getChildren().clear();
                                hbox.getChildren().addAll(button, pane, label);
                                buttonOnRight = false;
                            }
                        }
                        label.setText(conflict != null ? conflict.getKey() : "<null>");
                    }
                    setGraphic(hbox);
                }
            }
        };
        return mergeCellFactory;
    }

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createLeftCellFactory() {
        Callback<ListView<Conflict>, ListCell<Conflict>> mergeCellFactory = param -> new MergeListCell() {
            @Override
            void handleAction(ActionEvent event) {
                if (lastSelected != null) {
                    System.out.println("MergeListCell.left " + lastSelected);
                    lastSelected.chooseLeft();
                    getListView().refresh();
                }
            }

            @Override
            void init() {
                button.setText(">>");
            }

            @Override
            protected void updateItem(Conflict conflict, boolean empty) {
                super.updateItem(conflict, empty);
                setText(null);  // No text in label of super class
                if (empty || conflict == null) {
                    setGraphic(null);
                    lastSelected = null;
                } else {
                    lastSelected = conflict;
                    if (conflict.hasDecision() && conflict.isLeft()) {
                        hbox.getChildren().clear();
                        setText(conflict.getLeft().getName());
                    } else {
                        hbox.getChildren().clear();
                        button.setText(">>");
                        hbox.getChildren().addAll(label, pane, button);
                        label.setText(conflict.getLeft().getName());
                    }
                    setGraphic(hbox);
                }
            }
        };
        return mergeCellFactory;
    }

    public static class AAAAA {
        public static void main(String[] args) {
            System.out.println(new JFXPanel());
            System.out.println("MergeListCell.main");
            Platform.runLater(() -> N.r(() -> {
                javafx.stage.Stage stage = new javafx.stage.Stage();
                HBox root = new HBox();
                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.show();

                ListView<Conflict> mergeList = new ListView<>();
                mergeList.setCellFactory(createMergeCellFactory());
                Conflict c1 = new Conflict(new Stage().setName("a1").setId(1L), new Stage().setName("b1").setId(1L));
                Conflict c2 = new Conflict(new Stage().setName("a2").setId(2L), new Stage().setName("b2").setId(2L)).chooseRight();
                Conflict c3 = new Conflict(new Stage().setName("a3").setId(3L), new Stage().setName("b3").setId(3L)).chooseLeft();
                mergeList.getItems().addAll(c1, c2, c3);

                ListView<Conflict> leftList = new ListView<>(mergeList.getItems());
                leftList.setCellFactory(createLeftCellFactory());

                ListView<Conflict> rightList = new ListView<>(mergeList.getItems());
                root.getChildren().addAll(leftList, mergeList, rightList);


            }));

            new WaitLock().lock().lock();

        }
    }

}
