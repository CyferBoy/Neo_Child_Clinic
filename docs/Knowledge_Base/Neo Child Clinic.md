# Neo Child Clinic — Project Hub

> Generated notes start at [[Home]].

## Project Overview

Android application for managing patients, vaccinations, inventory, reminders, and statistics.

## Architecture

- [[Clean Architecture]]
    
- [[MVVM]]
    
- [[Offline-First Architecture]]
    
- [[Caching]]
    
- [[Error Handling]]
    

## Database

- [[Supabase]]
    
- [[Room Database]]
    
- [[Database Schema]]
    
- [[Row Level Security]]
    

## Features

- [[Patient Management]]
    
- [[Vaccination Management]]
    
- [[Inventory Management]]
    
- [[Due Vaccination]]
    
- [[Statistics Dashboard]]
    

## Development

- [[Known Bugs]]
    
- [[Architecture Decisions]]
    
- [[Feature Roadmap]]
    
- [[Release Notes]]
    
- [[Technical Debt]]
    
- [[Testing]]
    
- [[Backup and Restore]]
    

## Current Priorities

- Ensure cached data loads immediately.
    
- Synchronize local data with Supabase reliably.
    
- Exclude soft-deleted records from statistics.
    
- Verify inventory updates when editing vaccinations.
    
## Priority Findings

Each priority above was investigated; these are the verified answers.

- **Cached data** — reads are served by live Room `Flow`s, so they are fast regardless of
  the in-memory caches. See [[Caching]].
    
- **Sync reliability** — [[Known Bugs]] §2.3 (a `SingleFlight` waiter always reports
  success) and §3.1 (unbounded WorkManager chain growth).
    
- **Soft-deleted records in statistics** — **not reproducible at HEAD**. Only `reminders`
  retains an `is_deleted` column and nothing writes it, so every aggregate is clean by
  construction. The real gap is [[Known Bugs]] §1.4 — 9 of 16 reminder queries lack the
  filter, which affects the Due screen, patient history and backups, not statistics.
  See [[Statistics Dashboard]].
    
- **Inventory on edit** — confirmed broken. [[Known Bugs]] §1.1: an edit that increases
  quantity on a previously-failed batch moves real stock while the ledger records `FAILED`,
  so the dose is never restored on delete. See [[Inventory Management]].