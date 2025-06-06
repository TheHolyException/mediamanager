-- Add paused column to anime table for pause/resume functionality
-- This allows users to temporarily disable automatic downloads for specific anime subscriptions

ALTER TABLE anime ADD COLUMN IF NOT EXISTS bPaused BOOLEAN DEFAULT FALSE;