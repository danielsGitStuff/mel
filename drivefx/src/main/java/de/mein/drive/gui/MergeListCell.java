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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;

/**
 * Created by xor on 5/30/17.
 */
@SuppressWarnings("Duplicates")
public abstract class MergeListCell extends ListCell<Conflict> {
    HBox hbox = new HBox();
    Button button = new Button("x");
    Conflict lastSelected;
    // left to right
    protected boolean buttonOnRight = true;

    public MergeListCell() {
        super();
        hbox.getChildren().add(button);
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                handleAction(event);
            }
        });
        init();
        setGraphic(hbox);
        button.prefWidthProperty().bind(hbox.widthProperty());

    }

    abstract void handleAction(ActionEvent event);

    abstract void init();

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createMergeCellFactory(ListView<Conflict> leftList, ListView<Conflict> rightList) {
        Callback<ListView<Conflict>, ListCell<Conflict>> mergeCellFactory = param -> new MergeListCell() {


            @Override
            void handleAction(ActionEvent event) {
                if (lastSelected != null) {
                    System.out.println("MergeListCell.unsolve " + lastSelected);
                    lastSelected.chooseNothing();
                    leftList.refresh();
                    rightList.refresh();
                    getListView().refresh();
                    selectSame(getListView(), leftList, rightList);
                }
            }

            @Override
            void init() {

            }

            @Override
            protected void updateItem(Conflict conflict, boolean empty) {
                super.updateItem(conflict, empty);
                if (empty || conflict == null) {
                    setGraphic(null);
                    lastSelected = null;
                } else {
                    lastSelected = conflict;
                    if (!conflict.hasDecision()) {
                        button.setText("");
                        setTextAlignment(TextAlignment.RIGHT);
                    } else {
                        button.setText("x");
                        if (conflict.isRight()) {
                            button.setText(conflict.getLeft().getName() + " <<");
                        } else if (conflict.isLeft()) {
                            button.setText(">> " + conflict.getLeft().getName());
                            buttonOnRight = false;
                        }
                    }
                    setGraphic(hbox);
                }
            }
        };
        return mergeCellFactory;
    }

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createLeftCellFactory(ListView<Conflict> mergeList, ListView<Conflict> rightList) {
        Callback<ListView<Conflict>, ListCell<Conflict>> leftCellFactory = param -> new MergeListCell() {
            @Override
            void handleAction(ActionEvent event) {
                if (lastSelected != null) {
                    System.out.println("MergeListCell.left " + lastSelected);
                    lastSelected.chooseLeft();
                    getListView().refresh();
                    mergeList.refresh();
                    rightList.refresh();
                    selectSame(getListView(), mergeList, rightList);
                }
            }

            @Override
            void init() {
            }

            @Override
            protected void updateItem(Conflict conflict, boolean empty) {
                super.updateItem(conflict, empty);
                if (empty || conflict == null) {
                    setGraphic(null);
                    lastSelected = null;
                } else {
                    lastSelected = conflict;
                    if (conflict.hasDecision() && conflict.isLeft()) {
                        button.setText("");
                    } else {
                        button.setText(conflict.getLeft().getName() + " >>");
                    }
                    setGraphic(hbox);
                }

            }
        };
        return leftCellFactory;
    }

    public static Callback<ListView<Conflict>, ListCell<Conflict>> createRightCellFactory(ListView<Conflict> leftList, ListView<Conflict> mergeList) {
        Callback<ListView<Conflict>, ListCell<Conflict>> rightCellFactory = param -> new MergeListCell() {
            @Override
            void handleAction(ActionEvent event) {
                if (lastSelected != null) {
                    System.out.println("MergeListCell.left " + lastSelected);
                    lastSelected.chooseRight();
                    getListView().refresh();
                    mergeList.refresh();
                    leftList.refresh();
                    selectSame(getListView(), leftList, mergeList);
                }
            }

            @Override
            void init() {
                setText("");
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
                    if (!conflict.hasDecision() || conflict.isLeft()) {
                        button.setText("<< " + conflict.getLeft().getName());
                    } else {
                        button.setText("");
                    }
                    setGraphic(hbox);
                }
            }
        };
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
            System.out.println("MergeListCell.main");
            Platform.runLater(() -> N.r(() -> {
                javafx.stage.Stage stage = new javafx.stage.Stage();
                HBox root = new HBox();
                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.show();

                ListView<Conflict> mergeList = new ListView<>();
                Conflict c1 = new Conflict(new Stage().setName("a1").setId(1L), new Stage().setName("b1").setId(1L));
                Conflict c2 = new Conflict(new Stage().setName("a2").setId(2L), new Stage().setName("b2").setId(2L)).chooseRight();
                Conflict c3 = new Conflict(new Stage().setName("a3").setId(3L), new Stage().setName("b3").setId(3L)).chooseLeft();
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
