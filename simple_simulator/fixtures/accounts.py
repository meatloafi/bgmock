from decimal import Decimal

class AccountFixture:
    def __init__(self, client_a, client_b, clearing, config, logger):
        self.client_a = client_a
        self.client_b = client_b
        self.clearing = clearing
        self.config = config
        self.log = logger

    def __enter__(self):
        self.setup()
        return self  # so you can access it inside `with` if needed

    def __exit__(self, exc_type, exc_value, traceback):
        self.cleanup()
        # Do not suppress exceptions
        return False

    def setup(self):
        acc = self.config.account_number
        bal = Decimal("10000")

        self.log("Setting up accounts...")

        data, st = self.client_a.create_account(acc, "Jane Doe", bal)
        if st != 201:
            raise RuntimeError(f"Bank A setup failed: {data}")

        data, st = self.client_b.create_account(acc, "John Doe", bal)
        if st != 201:
            raise RuntimeError(f"Bank B setup failed: {data}")

        data, st = self.clearing.create_bank_mapping(acc, self.config.bank_a_clearing, self.config.bankgood_number_a, self.config.bank_a_name)
        if st != 201:
            raise RuntimeError(f"Bank A mapping failed: {data}")

        data, st = self.clearing.create_bank_mapping(acc, self.config.bank_b_clearing, self.config.bankgood_number_b, self.config.bank_b_name)
        if st != 201:
            raise RuntimeError(f"Bank B mapping failed: {data}")

    def cleanup(self):
        acc = self.config.account_number

        self.log("Cleaning up accounts...")


        self.client_a.delete_account(acc)
        self.client_b.delete_account(acc)


        self.clearing.delete_bank_mapping(self.config.bankgood_number_a)
        self.clearing.delete_bank_mapping(self.config.bankgood_number_b)