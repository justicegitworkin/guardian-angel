package com.safeharborsecurity.app.ui.points

import androidx.lifecycle.ViewModel
import com.safeharborsecurity.app.data.local.entity.PointTransactionEntity
import com.safeharborsecurity.app.data.local.entity.PointsBalanceEntity
import com.safeharborsecurity.app.data.repository.PointsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class PointsViewModel @Inject constructor(
    private val pointsRepository: PointsRepository
) : ViewModel() {
    val balance: Flow<PointsBalanceEntity?> = pointsRepository.getBalance()
    val recentTransactions: Flow<List<PointTransactionEntity>> =
        pointsRepository.getRecentTransactions(50)
}
