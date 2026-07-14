package com.example.data

import kotlinx.coroutines.flow.Flow

class DialogRepository(private val dialogDao: DialogDao) {
    val allConfigs: Flow<List<DialogConfig>> = dialogDao.getAllConfigs()

    suspend fun getConfigById(id: Int): DialogConfig? {
        return dialogDao.getConfigById(id)
    }

    suspend fun insert(config: DialogConfig): Long {
        return dialogDao.insertConfig(config)
    }

    suspend fun update(config: DialogConfig) {
        dialogDao.updateConfig(config)
    }

    suspend fun deleteById(id: Int) {
        dialogDao.deleteConfigById(id)
    }
}
