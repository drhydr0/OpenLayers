/**
 * Crash handler, log buffer, diagnostics screen, and export.

All logging goes through a wrapper here, never bare android.util.Log, so lines reach the in-app buffer.
 */
package io.github.drhydr0.layers.diagnostics
