package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY subscriptionId ASC, sortOrder ASC, id DESC")
    fun observeAll(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ServerProfileEntity?

    @Query("SELECT * FROM server_profiles ORDER BY subscriptionId ASC, sortOrder ASC, id DESC")
    suspend fun getAllOnce(): List<ServerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ServerProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ServerProfileEntity>): List<Long>

    @Update
    suspend fun update(profile: ServerProfileEntity)

    @Query("UPDATE server_profiles SET latencyMs = :latencyMs WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Long?)

    @Query("UPDATE server_profiles SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun updateSortOrders(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            updateSortOrder(id, index)
        }
    }

    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM server_profiles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: Long)

    @Transaction
    suspend fun replaceSubscriptionProfiles(
        subscriptionId: Long,
        profiles: List<ServerProfileEntity>
    ): List<Long> {
        deleteBySubscription(subscriptionId)
        return if (profiles.isNotEmpty()) insertAll(profiles) else emptyList()
    }
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY id DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions ORDER BY id DESC")
    suspend fun getAllOnce(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET name = :name WHERE id = :id")
    suspend fun renameById(id: Long, name: String)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RoutingRuleDao {
    @Query("SELECT * FROM routing_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules WHERE enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledOnce(): List<RoutingRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RoutingRuleEntity): Long

    @Update
    suspend fun update(rule: RoutingRuleEntity)

    @Query("UPDATE routing_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM routing_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
