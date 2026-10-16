package com.neochildclinic.feature.inventory.domain

/**
 * Thrown when a stock movement cannot proceed because the batch does not hold enough doses.
 *
 * It extends IllegalStateException so existing catch sites keep working, but callers that
 * need to tell "the shelf was short" apart from "something broke while moving stock" catch
 * this type specifically. That distinction matters in ClinicalVaccinationService: a shortfall
 * must be recorded as a FAILED inventory_deductions row and still save the visit, while any
 * other failure has to propagate so the surrounding transaction rolls back - swallowing it
 * left the dose deducted with a FAILED ledger row that nothing ever restored.
 */
class InsufficientStockException(message: String) : IllegalStateException(message)