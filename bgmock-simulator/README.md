# BGMock - Digital Twin Simulator

This project is a Python-based simulator and digital twin for the BGMock banking microservices.

## Setup

1.  **Create and activate a virtual environment:**
    ```bash
    python -m venv venv
    # On Windows
    # .\venv\Scripts\activate
    # On macOS/Linux
    # source venv/bin/activate
    ```

2.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

3.  **Configure environment:**
    - Copy `.env.example` to a new file named `.env`.
    - Update the service URLs in `.env` to match your local environment.

## Running the Simulator

```bash
streamlit run src/ui/streamlit_app.py
```

