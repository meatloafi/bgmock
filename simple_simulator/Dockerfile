FROM python:3.12-slim

WORKDIR /app

# Copy your code
COPY . .

# Install dependencies if you have a requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Default command
CMD ["python", "main.py"]
