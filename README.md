# API de Investimentos

Esta é uma API RESTful para investimento. Nesta primeira etapa, ela já fornece funcionalidades para criar, ler, atualizar e excluir (CRUD) usuários.

## Tecnologias Utilizadas

![Java Logo](https://www.vectorlogo.zone/logos/java/java-icon.svg)
![Docker Logo](https://www.vectorlogo.zone/logos/docker/docker-icon.svg)
![MySQL Logo](https://www.vectorlogo.zone/logos/mysql/mysql-icon.svg)

## Conecte-se no LinkedIn

[![LinkedIn Logo](https://www.vectorlogo.zone/logos/linkedin/linkedin-icon.svg)](https://www.linkedin.com/in/lindembergue-frank-b991202b7/)

## Funcionalidades

- **Criar Usuário**: Cria um novo usuário e retorna o usuário criado com o ID no cabeçalho.
- **Buscar Usuário por ID**: Recupera um usuário existente pelo ID.
- **Listar Todos os Usuários**: Obtém a lista de todos os usuários cadastrados.
- **Atualizar Usuário**: Atualiza os dados de um usuário existente pelo ID.
- **Excluir Usuário**: Remove um usuário existente pelo ID.

## Endpoints

### Criar usuário

- **URL**: `/v1/users`
- **Método**: `POST`
- **Corpo da Requisição**:
  ```json
  {
    "username": "string",
    "email": "string",
    "password": "string"
  }
  
- **Resposta**:
  - Código: `201 Created`
  ```json
  {
    "id": "UUID",
    "username": "string",
    "email": "string",
    "password": "string",
    "creationTimestamp": "ISO8601",
    "updateTimestamp": "ISO8601"
  }
  
### Buscar usuário por ID
- **URL**: `/v1/users/{id}`
- **Método**: `GET`
- **Parâmetros**:
  - `id` (Path) - `ID do usuário`
- **Resposta**:
  - Código: `200 OK`

### Listar todos os usuários
- **URL**:
- **Método**: `GET`
- **Resposta**:
  - Código: '200 OK'
  ```Json
  {
    "id": "UUID",
    "username": "string",
    "email": "string",
    "password": "string",
    "creationTimestamp": "ISO8601",
    "updateTimestamp": "ISO8601"
  }
### Atualizar usuário

- **URL**: `/v1/users/{id}`
- **Método**: `PUT`
- **Parâmetros**:
  - `id` (Path) - `ID do usuário`
- **Resposta**: 
  - Código: `200 OK`
  ```Json
  {
    "username": "string",
    "email": "string",
    "password": "strin
  }

### Excluir usuário 

- **URL**: `/v1/users/{id}`
- **Método**: `DELETE`
- **Parâmetros**:
  - `id` (Path) - ID do usuário
- **Resposta**:
  - Código: `204 No Content`

  ```Json
  {
    "username": "string",
    "password": "string",
  }

## Como Contribuir

### Dê um Fork
1. Clique no botão `Fork` no canto superior direito da página para criar uma cópia deste repositório no seu GitHub.

### Dê um Star
1. Se você achou este repositório útil, dê um `Star` clicando na estrela no canto superior direito da página.

### Clone o Repositório
1. Após dar o Fork, clone o repositório para a sua máquina local:
    ```sh
    git clone https://github.com/LindembergueFrank/API-Investimentos
    ```

### Faça as Suas Alterações
1. Crie uma nova branch para as suas alterações:
    ```sh
    git checkout -b minha-nova-feature
    ```

2. Faça as alterações necessárias e comite as mudanças:
    ```sh
    git commit -m "Minha nova feature"
    ```

3. Empurre as alterações para o seu repositório forkado:
    ```sh
    git push origin minha-nova-feature
    ```

### Abra um Pull Request
1. Vá até a página do seu repositório forkado no GitHub e clique no botão `New Pull Request` para enviar suas alterações para revisão.

## Contribuições

Este repositório é público e qualquer um pode utilizá-lo. Se você tiver alguma dúvida ou sugestão, por favor, abra uma _issue_.
