package com.elotest.myapplication

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val recyclerView: RecyclerView
        get() = findViewById<RecyclerView>(R.id.recyclerView)

    private val mainAdapter: MainAdapter
        get() = recyclerView.adapter as MainAdapter

    private val _items: MutableLiveData<List<MainListItem>> = MutableLiveData()

    private fun getInitialItems(): List<MainListItem> =
            listOf(
                    MainListItem.DownloadableItem(id = 222, downloadStatus = DownloadStatus.TO_DOWNLOAD),
                    MainListItem.DownloadableItem(id = 223, downloadStatus = DownloadStatus.TO_DOWNLOAD),
                    MainListItem.DownloadableItem(id = 224, downloadStatus = DownloadStatus.TO_DOWNLOAD),
                    MainListItem.DownloadableItem(id = 225, downloadStatus = DownloadStatus.TO_DOWNLOAD),
            ).plus(
                    (0..30L).map { MainListItem.EmptyItem(id = it) }
            )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        observeItemsChange()
        populateListWithInitialItems()
    }

    private fun populateListWithInitialItems() {
        val action = ListAction.AddItems(items = getInitialItems())
        performActionOnList(action)
    }

    private fun observeItemsChange() {
        _items.observe(this) {
            mainAdapter.submitList(it)
        }
    }

    private fun setupRecyclerView() {
        recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = createAdapter()
        }
    }

    private fun createAdapter(): MainAdapter {
        return MainAdapter(
                onDownloadClicked = { simulateDownload(it) }
        )
    }

    @Synchronized
    private fun simulateDownload(item: MainListItem.DownloadableItem) {
        val action = ListAction.UpdateDownloadStatus(
                itemId = item.id,
                downloadStatus = DownloadStatus.DOWNLOADING
        )
        performActionOnList(action)
        returnToToDownloadStateWithDelay(item.id)
    }

    private fun returnToToDownloadStateWithDelay(itemId: Long) {
        Handler().postDelayed({
            val action = ListAction.UpdateDownloadStatus(
                    itemId = itemId,
                    downloadStatus = DownloadStatus.TO_DOWNLOAD
            )
            performActionOnList(action)
        }, 5000L)
    }

    @Synchronized
    private fun performActionOnList(action: ListAction) {

        fun addItems(action: ListAction.AddItems) {
            val newItems: List<MainListItem> = _items.value?.toMutableList() ?: emptyList()
            _items.postValue(newItems.plus(action.items))
        }

        fun updateDownloadStatus(action: ListAction.UpdateDownloadStatus) {
            val newItems: MutableList<MainListItem> = _items.value?.toMutableList()
                    ?: mutableListOf()
            val currentItemWithIndex =
                    newItems.withIndex()
                            .lastOrNull { it.value.id == action.itemId }

            currentItemWithIndex?.let {
                val updatedItem = (it.value as MainListItem.DownloadableItem).copy(downloadStatus = action.downloadStatus)
                newItems[it.index] = updatedItem
                _items.value = newItems
            }
        }

        when (action) {
            is ListAction.UpdateDownloadStatus -> updateDownloadStatus(action)
            is ListAction.AddItems -> addItems(action)
        }
    }

    sealed class ListAction {
        data class UpdateDownloadStatus(val itemId: Long, val downloadStatus: DownloadStatus) : ListAction()
        data class AddItems(val items: List<MainListItem>) : ListAction()
    }

}

sealed class MainListItem(open val id: Long) {
    data class DownloadableItem(override val id: Long, val downloadStatus: DownloadStatus) : MainListItem(id)
    data class EmptyItem(override val id: Long) : MainListItem(id)
}


enum class DownloadStatus {
    TO_DOWNLOAD, DOWNLOADING
}

class MainAdapter(
        private val onDownloadClicked: (item: MainListItem.DownloadableItem) -> Unit
) : ListAdapter<MainListItem, RecyclerView.ViewHolder>(MainDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            DOWNLOADABLE_VIEW_TYPE -> {
                val layoutInflater = LayoutInflater.from(parent.context)
                val itemView = layoutInflater.inflate(R.layout.view_dowanlodable_item, parent, false)
                return DownloadableViewHolder(itemView, onDownloadClicked)
            }
            EMPTY_VIEW_TYPE -> {
                val layoutInflater = LayoutInflater.from(parent.context)
                val itemView = layoutInflater.inflate(R.layout.view_empty_item, parent, false)
                return EmptyViewHolder(itemView)
            }
            else -> error("")
        }

    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MainListItem.DownloadableItem -> DOWNLOADABLE_VIEW_TYPE
            is MainListItem.EmptyItem -> EMPTY_VIEW_TYPE
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DownloadableViewHolder -> holder.bind(getItem(position) as MainListItem.DownloadableItem)
            is EmptyViewHolder -> {
                // no-op
            }
        }
    }

    companion object {
        private const val DOWNLOADABLE_VIEW_TYPE = 1
        private const val EMPTY_VIEW_TYPE = 2
    }

}

class DownloadableViewHolder(
        itemView: View,
        private val onDownloadClicked: (item: MainListItem.DownloadableItem) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    private val changingVisibilityProgressBar: ProgressBar
        get() = itemView.findViewById(R.id.changingVisibilityProgressBar)

    private val downloadButton: Button
        get() = itemView.findViewById(R.id.downloadButton)

    fun bind(item: MainListItem.DownloadableItem) {
        when (item.downloadStatus) {
            DownloadStatus.TO_DOWNLOAD -> {
                downloadButton.isEnabled = true
                downloadButton.setOnClickListener {
                    onDownloadClicked.invoke(item)
                }
                changingVisibilityProgressBar.visibility = View.GONE
            }
            DownloadStatus.DOWNLOADING -> {
                downloadButton.isEnabled = false
                changingVisibilityProgressBar.visibility = View.VISIBLE
            }
        }
    }

}

class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

object MainDiff : DiffUtil.ItemCallback<MainListItem>() {
    override fun areItemsTheSame(oldItem: MainListItem, newItem: MainListItem): Boolean {
        return when {
            oldItem is MainListItem.DownloadableItem && newItem is MainListItem.DownloadableItem -> {
                oldItem.id == newItem.id
            }
            oldItem is MainListItem.EmptyItem && newItem is MainListItem.EmptyItem -> {
                oldItem.id == newItem.id
            }
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: MainListItem, newItem: MainListItem): Boolean {
        return when {
            oldItem is MainListItem.DownloadableItem && newItem is MainListItem.DownloadableItem -> {
                oldItem == newItem
            }
            oldItem is MainListItem.EmptyItem && newItem is MainListItem.EmptyItem -> {
                oldItem == newItem
            }
            else -> false
        }
    }

    override fun getChangePayload(oldItem: MainListItem, newItem: MainListItem): Any? {
//        return super.getChangePayload(oldItem, newItem)
        return Unit
    }

}