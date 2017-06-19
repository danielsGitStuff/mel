package de.mein.drive.gui;

import de.mein.auth.gui.PopupContentFX;
import de.mein.auth.service.MeinService;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.sync.Conflict;
import de.mein.drive.service.sync.ConflictSolver;
import de.mein.drive.service.sync.EmptyRowConflict;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


/**
 * Created by xor on 5/30/17.
 */
public class DriveFXConflictSolverControllerList implements PopupContentFX {
    @FXML
    private ListView<Conflict> listLeft, listRight, listMerge;
    private MeinDriveClientService meinDriveClientService;
    private ConflictSolver conflictSolver;


    @Override
    public String onOkCLicked() {
        System.out.println("DriveFXConflictSolverController.onOkCLicked");
        if (conflictSolver.isSolved()) {
            meinDriveClientService.addJob(new CommitJob());
            return null;
        }
        return "you bloody idiot!";
    }

    @Override
    public void init(MeinService meinService, Object msgObject) {
        this.meinDriveClientService = (MeinDriveClientService) meinService;
        conflictSolver = (ConflictSolver) msgObject;
        meinDriveClientService.addConflictSolver(conflictSolver);
        System.out.println("DriveFXConflictSolverController.init");
        MergeListCell.setup(listLeft, listMerge, listRight);
        listLeft.getItems().addAll(prepareConflicts(conflictSolver.getConflicts()));
    }

    /**
     * sorts, indents and adds empty rows
     *
     * @param conflicts
     * @return
     */
    private List<Conflict> prepareConflicts(Collection<Conflict> conflicts) {
        List<Conflict> result = new ArrayList<>();
        List<Conflict> rootConflicts = new ArrayList<>();
        List<Conflict> independentConflicts = new ArrayList<>();
        for (Conflict conflict : conflicts) {
            if (conflict.getDependsOn() == null)
                rootConflicts.add(conflict);
            else
                independentConflicts.add(conflict);
        }
        for (Conflict root : rootConflicts) {
            result.add(root);
            traversalAdding(result, root.getDependents());
            if (root.getDependents().size() > 0)
                result.add(new EmptyRowConflict());
        }
        for (Conflict conflict : independentConflicts) {
            result.add(conflict);
        }
        return result;
    }

    private void traversalAdding(List<Conflict> result, Set<Conflict> stuffToTraverse) {
        for (Conflict conflict : stuffToTraverse) {
            result.add(conflict);
            if (conflict.getDependents().size() > 0) {
                traversalAdding(result, conflict.getDependents());
            }
        }
    }

}
