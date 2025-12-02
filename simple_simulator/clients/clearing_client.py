import requests

class ClearingTestClient:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        
    
    def create_bank_mapping(self, account_number: str, clearing_number: str, bankgood_number: str, bank_name: str):
        payload = {
            "accountNumber": account_number,
            "clearingNumber": clearing_number,
            "bankgoodNumber": bankgood_number,
            "bankName": bank_name
        }
        response = requests.post(f"{self.base_url}/clearing/bank-mapping", json=payload, timeout=15)
        return response.json, response.status_code
    
    def delete_bank_mapping(self, bankgood_nr: str): 
        response = requests.delete(f"{self.base_url}/clearing/bank-mapping/{bankgood_nr}")
        return {}, response.status_code