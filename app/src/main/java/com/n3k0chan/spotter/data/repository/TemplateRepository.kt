package com.n3k0chan.spotter.data.repository

import com.n3k0chan.spotter.data.db.dao.TemplateDao
import com.n3k0chan.spotter.data.db.entities.Template
import com.n3k0chan.spotter.data.db.entities.TemplateExercise
import com.n3k0chan.spotter.data.db.entities.TemplateWithExercises
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val dao: TemplateDao) {

    fun observeAll(): Flow<List<TemplateWithExercises>> = dao.observeAll()
    suspend fun get(id: Long): TemplateWithExercises? = dao.getById(id)

    suspend fun create(name: String, items: List<TemplateExercise>): Long {
        val templateId = dao.insertTemplate(Template(name = name.trim()))
        val withParent = items.mapIndexed { idx, it -> it.copy(templateId = templateId, orderIndex = idx) }
        if (withParent.isNotEmpty()) dao.insertItems(withParent)
        return templateId
    }

    suspend fun rename(id: Long, name: String) =
        dao.updateTemplate(Template(id = id, name = name.trim()))

    suspend fun replaceItems(templateId: Long, items: List<TemplateExercise>) {
        val withParent = items.mapIndexed { idx, it -> it.copy(templateId = templateId, orderIndex = idx) }
        dao.replaceItems(templateId, withParent)
    }

    suspend fun delete(template: Template) = dao.deleteTemplate(template)
}
