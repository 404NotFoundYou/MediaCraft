package deckers.thibault.aves.channel

// A BK-tree only visits Hamming distances that can intersect the query radius.
internal class HashIndex {
    private class Node(val hash: Long) {
        val ids = mutableListOf<Int>()
        val children = mutableMapOf<Int, Node>()
    }
    private var root: Node? = null

    fun add(hash: Long, id: Int) {
        var node = root ?: Node(hash).also { root = it }
        while (true) {
            val distance = java.lang.Long.bitCount(hash xor node.hash)
            if (distance == 0) { node.ids += id; return }
            node = node.children.getOrPut(distance) { Node(hash) }
        }
    }

    fun find(hash: Long, radius: Int, result: MutableSet<Int>) {
        val pending = ArrayDeque<Node>()
        pending.addLast(root ?: return)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val distance = java.lang.Long.bitCount(hash xor node.hash)
            if (distance <= radius) result.addAll(node.ids)
            for ((edge, child) in node.children) if (edge in distance - radius..distance + radius) pending.addLast(child)
        }
    }
}
