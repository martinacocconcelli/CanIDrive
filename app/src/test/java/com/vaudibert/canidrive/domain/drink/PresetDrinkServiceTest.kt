package com.vaudibert.canidrive.domain.drink

import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import java.util.*

internal class PresetDrinkServiceTest {

    private lateinit var presetService: PresetDrinkService<PresetDrink>
    private lateinit var exportPreset: List<PresetDrink>
    private lateinit var testIIngestor: TestIIngestor

    private val presetA = PresetDrink("A", 10.0, 10.0, 1)
    private val presetB = PresetDrink("B", 20.0, 20.0,2)
    private val presetC = PresetDrink("C", 30.0, 30.0, 3)

    private var presetPopulation = listOf(presetA, presetB, presetC)

    private fun setupBasic() {
        presetService = PresetDrinkService {
                name: String, volume: Double, degree: Double ->
            PresetDrink(name, volume, degree)
        }
        presetService.onPresetsChanged = {
            exportPreset = it
        }
        testIIngestor = TestIIngestor()
        presetService.ingestionService = testIIngestor
    }

    private fun setupFull() {
        setupBasic()
        presetService.populate(presetPopulation)
        presetService.selectedPreset = presetB
    }

    @Test
    fun `preset service is initialized empty`() {
        setupBasic()

        presetService.populate(emptyList())

        assertEquals(0, exportPreset.size)
        assertNull(presetService.selectedPreset)
    }

    @Test
    fun `preset service sorts its populated presets`() {
        setupFull()

        assertEquals("C", exportPreset[0].name)
        assertEquals("B", exportPreset[1].name)
        assertEquals("A", exportPreset[2].name)
    }

    @Test
    fun `preset service increases count on effective ingestion`() {
        setupFull()

        presetService.ingest(Date())
        presetService.ingest(Date())

        assertEquals("B", presetService.selectedPreset!!.name)
        assertEquals(4, presetService.selectedPreset!!.count)
        assertEquals(2, testIIngestor.ingestedList.size)
    }

    @Test
    fun `selected preset can be updated`() {
        setupFull()

        presetService.updateSelectedPreset("BB", 22.0, 18.0)

        assertEquals("BB", presetService.selectedPreset!!.name)
        assertEquals(22.0, presetService.selectedPreset!!.volume)
        assertEquals(18.0, presetService.selectedPreset!!.degree)
    }

    @Test
    fun `preset can be removed`() {
        setupFull()

        presetService.removePreset(presetB)

        fail<Boolean>()
    }
}