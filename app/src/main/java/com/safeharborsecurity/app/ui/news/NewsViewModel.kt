package com.safeharborsecurity.app.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safeharborsecurity.app.data.local.entity.NewsArticleEntity
import com.safeharborsecurity.app.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsUiState(
    val articles: List<NewsArticleEntity> = emptyList(),
    val unreadCount: Int = 0,
    val isRefreshing: Boolean = false,
    val selectedSource: String? = null // null = all sources
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository
) : ViewModel() {

    private val _selectedSource = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NewsUiState> = combine(
        _selectedSource.flatMapLatest { source ->
            if (source == null) newsRepository.getAllArticles()
            else newsRepository.getArticlesBySource(source)
        },
        newsRepository.getUnreadCount(),
        _isRefreshing
    ) { articles, unreadCount, isRefreshing ->
        NewsUiState(
            articles = articles,
            unreadCount = unreadCount,
            isRefreshing = isRefreshing,
            selectedSource = _selectedSource.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsUiState())

    fun selectSource(source: String?) {
        _selectedSource.value = source
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            newsRepository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            newsRepository.markAllAsRead()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            newsRepository.syncAllFeeds()
            _isRefreshing.value = false
        }
    }

    fun getSourceColor(source: String): Long = newsRepository.getSourceColor(source)
}
