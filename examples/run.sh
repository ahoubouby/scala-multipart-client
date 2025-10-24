#!/bin/bash

# Script to run examples easily

set -e

EXAMPLE_NAME="${1:-help}"

function show_help {
    echo "Usage: ./run.sh [example-name]"
    echo ""
    echo "Available examples:"
    echo "  server    - Start the mock shipping server"
    echo "  client    - Run the shipping label client (with mock server)"
    echo "  help      - Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  SHIPPING_API_URL    - API base URL (default: http://localhost:8080 for mock)"
    echo "  SHIPPING_API_TOKEN  - API token (default: test-token)"
    echo "  OUTPUT_DIR          - Output directory (default: ./output)"
    echo ""
    echo "Examples:"
    echo "  # Start mock server"
    echo "  ./run.sh server"
    echo ""
    echo "  # Run client with mock server (in another terminal)"
    echo "  ./run.sh client"
    echo ""
    echo "  # Run client with real API"
    echo "  SHIPPING_API_URL=https://api.real.com SHIPPING_API_TOKEN=abc123 ./run.sh client"
}

case "$EXAMPLE_NAME" in
    server)
        echo "Starting mock shipping server..."
        sbt "examples/runMain example.MockShippingServer"
        ;;
    client)
        echo "Running shipping label client..."
        export SHIPPING_API_URL="${SHIPPING_API_URL:-http://localhost:8080}"
        export SHIPPING_API_TOKEN="${SHIPPING_API_TOKEN:-test-token}"
        export OUTPUT_DIR="${OUTPUT_DIR:-./output}"

        echo "Configuration:"
        echo "  API URL: $SHIPPING_API_URL"
        echo "  Output: $OUTPUT_DIR"
        echo ""

        mkdir -p "$OUTPUT_DIR"
        sbt "examples/runMain example.ShippingLabelClient"
        ;;
    help|*)
        show_help
        ;;
esac
