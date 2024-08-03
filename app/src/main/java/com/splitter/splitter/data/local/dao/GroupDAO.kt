package com.splitter.splitter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitter.splitter.data.local.entities.GroupEntity
import com.splitter.splitter.model.Group

@Dao
interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity)

    @Query("SELECT * FROM groups")
    suspend fun getAllGroups(): List<Group>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: Int): Group?
}
