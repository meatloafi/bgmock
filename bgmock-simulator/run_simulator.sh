#!/bin/bash
# BGMock Simulator Runner
# This script sets up the environment and runs the simulator

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Set Python path to include src directory
export PYTHONPATH="${SCRIPT_DIR}/src"

# Activate virtual environment and run streamlit
./venv/bin/streamlit run src/ui/streamlit_app.py --server.headless true
