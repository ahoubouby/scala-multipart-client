# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added

#### Enhanced MultipartPart API
- Added `isExcel` - Check if part is Excel/spreadsheet
- Added `isCsv` - Check if part is CSV
- Added `isText` - Check if part is text-based
- Added `isBinary` - Check if part is binary data
- Added `asString(encoding: String)` - Get content with custom encoding
- Added `filename` - Get filename for form-data parts
- Added `contentId` - Get content-id for related parts
- Added `contentLocation` - Get content-location
- Added `headers` - Access all headers as Map
- Added `sizeKB` - Size in kilobytes
- Added `sizeMB` - Size in megabytes
- Added `sizeFormatted` - Human-readable size string (e.g., "1.23 KB")
- Added `isValidPdf` - Validate PDF magic bytes
- Added `imageFormat` - Detect image format from magic bytes (JPEG, PNG, GIF)

#### Test Infrastructure
- Added resource-based test fixtures
- Created `src/test/resources/multipart/` with realistic multipart data files:
  - `simple-form-data.txt` - Basic form data example
  - `form-data-with-file.txt` - Form data with file upload
  - `related-multipart.txt` - Multipart/related with JSON and PDF
  - `mixed-multipart.txt` - Multipart/mixed example
- Created `src/test/resources/test-data/` with sample data:
  - `sample.json` - Comprehensive shipping data
  - `sample.xml` - XML version of shipping data
- Updated `TestFixtures` to load data from resources instead of hardcoded strings

#### Examples
- Added `examples/` directory with runnable examples
- Added `ShippingLabelClient` - Complete example showing:
  - Making multipart requests
  - Parsing responses
  - Extracting metadata
  - Saving PDFs and images
  - Using enhanced part methods
- Added `MockShippingServer` - Local HTTP server for testing
- Added examples README with comprehensive documentation
- Added `run.sh` script for easy example execution

#### Build Configuration
- Updated `build.sbt` to multi-project structure
- Created separate `examples` project
- Added `play-ahc-ws-standalone` dependency for examples

### Changed
- `TestFixtures` now loads realistic data from resource files
- `MultipartPart.toString` now shows formatted size instead of byte count
- Improved error messages for missing resource files

### Documentation
- Added comprehensive examples documentation
- Added troubleshooting guide for common issues
- Added environment variable reference
- Updated README with examples section

## [0.1.0] - 2025-10-24

### Initial Release
- Generic multipart response parser
- Support for multipart/form-data, multipart/related, and multipart/mixed
- Type-safe API with sealed traits
- Pluggable classifier system
- Streaming-based parsing with Pekko
- HTTP client abstraction with Play WS implementation
- Content type detection
- Pattern-based part matching
- Comprehensive test suite (140+ tests)
