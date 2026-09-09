class DepartamentoNoEncontradoException(Exception):
    def __init__(self, departamento_id: str):
        self.departamento_id = departamento_id
        super().__init__(
            f"El departamento con ID '{departamento_id}' no existe"
        )


class DepartamentoDuplicadoException(Exception):
    def __init__(self, departamento_id: str):
        self.departamento_id = departamento_id
        super().__init__(
            f"El departamento con ID '{departamento_id}' ya existe"
        )