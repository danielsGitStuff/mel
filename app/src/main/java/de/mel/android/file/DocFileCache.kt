package de.mel.android.file

import androidx.documentfile.provider.DocumentFile
import de.mel.Lok
import java.util.*

class DocFileCache(val rootDocFile: DocumentFile, val maxItems: Int) {
    private val root = DocTreeRoot(this, rootDocFile)

    class DocTreeRoot(cache: DocFileCache, rootDocFile: DocumentFile) : DocTreeNode(cache, rootDocFile, 1, "[root]", null) {
        fun findDoc(queue: Queue<String>): DocumentFile? {
            val currentPath = mutableSetOf<DocTreeNode>()
            currentPath.add(this)
            return super.findDoc(queue, currentPath)
        }
    }

    open class DocTreeNode(val cache: DocFileCache, val docFile: DocumentFile, var importance: Int, val name: String, val parentNode: DocTreeNode?) {
        val nodes = mutableMapOf<String, DocTreeNode>()

        /**
         * substract importance from subnodes that we do not address yet
         */
        fun lessenImportanceOfChildren(exceptThis: String?) {
            if (exceptThis == null) {
                nodes.values.forEach { it.lessenImportanceOfChildren(null) }
            } else
                nodes.filter { entry -> !entry.key.equals(exceptThis) }.forEach {
                    it.value.lessenImportanceOfChildren(null)
                }
        }

        /**
         * find a DocumentFile or create if necessary
         */
        fun findDoc(queue: Queue<String>, currentPath: MutableSet<DocTreeNode>): DocumentFile? {
            importance++
            if (queue.size == 0)
                return docFile

            val name = queue.poll()
            currentPath.add(this)
            if (nodes.containsKey(name)) {
                //got a matching child
                lessenImportanceOfChildren(name)
                return nodes[name]!!.findDoc(queue, currentPath)
            }
            // doc is not present in tree now
            return cache.createNode(this, name, docFile, currentPath)?.findDoc(queue, currentPath);
        }

        fun isLeaf() = nodes.isEmpty()
        fun removeThyself() {
            parentNode?.nodes?.remove(name)

        }

    }

    private val allNodes = mutableSetOf<DocTreeNode>()

    private fun createNode(parentNode: DocTreeNode, name: String, docFile: DocumentFile, currentPath: MutableSet<DocTreeNode>): DocTreeNode? {
        var nodeDoc: DocumentFile? = docFile.findFile(name)
        if (nodeDoc == null) {
            return null
//            nodeDoc = docFile.createFile(SAFAccessor.MIME_GENERIC, name)
        }
        if (!name.equals(nodeDoc!!.name))
            Lok.debug()
        val node = DocTreeNode(this, nodeDoc!!, 1, name, parentNode)
        parentNode.nodes[name] = node
        freeSpace(currentPath)
        currentPath.add(node)
        allNodes.add(node)
        return node
    }

    // find a node to remove
    private fun freeSpace(obtainThese: Set<DocTreeNode>) {
        if (allNodes.size < maxItems)
            return

        val smallLeaf = allNodes
                .filter { !obtainThese.contains(it) && it.isLeaf() }
                .sortedBy { it.importance }
                .firstOrNull()
        if (smallLeaf == null) {
            Lok.debug("could not free any space, because no leafs are present")
            return
        }
        smallLeaf.removeThyself()
        allNodes.remove(smallLeaf)
    }


    @Synchronized
    fun findDoc(parts: Array<String>): DocumentFile? {
        if (parts.isEmpty())
            return root.docFile;
        var docFile: DocumentFile? = null
        val queue: Queue<String> = LinkedList()
        queue.addAll(parts)
        docFile = root.findDoc(queue)
        return docFile
    }


}