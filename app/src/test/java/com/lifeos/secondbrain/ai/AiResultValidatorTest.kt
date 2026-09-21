package com.lifeos.secondbrain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiResultValidatorTest {
    @Test
    fun acceptsStructuredItemsWithExplicitIsoDueDate() {
        val result = AiOrganizeResult(
            listOf(
                AiItem(
                    type = "task",
                    title = "联系老师",
                    status = "active",
                    due = "2026-09-22",
                    tags = listOf("学校")
                )
            )
        )

        assertEquals(result, AiResultValidator.validate(result))
    }

    @Test
    fun rejectsUnknownTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            AiResultValidator.validate(AiOrganizeResult(listOf(AiItem(type = "habit", title = "晨跑"))))
        }
    }

    @Test
    fun rejectsNonIsoDueDatesRatherThanGuessing() {
        assertThrows(IllegalArgumentException::class.java) {
            AiResultValidator.validate(
                AiOrganizeResult(listOf(AiItem(type = "task", title = "交作业", due = "下周二")))
            )
        }
    }
}
