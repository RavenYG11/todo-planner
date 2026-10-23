package id.rama.todoplanner.data

import kotlinx.coroutines.flow.Flow

/** Lapisan tipis di atas DAO — ViewModel hanya bicara ke sini. */
class Repository(
    private val activityDao: ActivityDao,
    private val targetDao: TargetDao
) {
    val activities: Flow<List<ActivityEntity>> = activityDao.observeAll()
    val targets: Flow<List<TargetEntity>> = targetDao.observeAll()

    suspend fun getActivity(id: Long): ActivityEntity? = activityDao.getById(id)
    suspend fun getAllActivitiesOnce(): List<ActivityEntity> = activityDao.getAllOnce()
    suspend fun getAllTargetsOnce(): List<TargetEntity> = targetDao.getAllOnce()
    suspend fun saveActivity(entity: ActivityEntity): Long =
        if (entity.id == 0L) activityDao.insert(entity) else { activityDao.update(entity); entity.id }
    suspend fun deleteActivity(entity: ActivityEntity) = activityDao.delete(entity)
    suspend fun saveTarget(entity: TargetEntity): Long =
        if (entity.id == 0L) targetDao.insert(entity) else { targetDao.update(entity); entity.id }
    suspend fun deleteTarget(entity: TargetEntity) = targetDao.delete(entity)
    suspend fun replaceAll(activities: List<ActivityEntity>, targets: List<TargetEntity>) {
        activityDao.deleteAll(); targetDao.deleteAll()
        activities.forEach { activityDao.insert(it.copy(id = 0)) }
        targets.forEach { targetDao.insert(it.copy(id = 0)) }
    }
}
