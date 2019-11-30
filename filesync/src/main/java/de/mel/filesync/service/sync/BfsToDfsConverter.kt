package de.mel.filesync.service.sync

import de.mel.auth.tools.Order
import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.StageSet
import de.mel.filesync.sql.dao.StageDao

class BfsToDfsConverter(val stageDao: StageDao) {
    val order = Order()
    fun convert(stageSet: StageSet) {
        stageDao.negateOrder(stageSet.id.v())
        val depths = stageDao.getMaxDepth(stageSet.id.v())
        val iterator = depths.iterator()
        while (iterator.hasNext()) {
            // We dive from this depth
            val startDepth = iterator.next()
            var currentStage = stageDao.getStageForDiving(stageSet.id.v(), startDepth)
            while (currentStage != null) {
                diveAndOrder(currentStage)
                currentStage = stageDao.getStageForDiving(stageSet.id.v(), startDepth)
            }
        }
    }

    /**
     * dive and assign order values to each stage
     */
    private fun diveAndOrder(currentStage: Stage) {
        if (currentStage.order >= 0)
            return
        currentStage.order = order.ord()
        stageDao.updateOrder(currentStage.id, currentStage.order)
        if (!currentStage.isDirectory)
            return
        // stage is a directory: dive here
        var child = stageDao.getChildForDiving(currentStage.id)
        while (child != null) {
            diveAndOrder(child)
            child = stageDao.getChildForDiving(currentStage.id)
        }
    }
}