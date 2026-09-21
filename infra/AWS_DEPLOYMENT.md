# Deploy na AWS — resumo e comandos

Registro do que foi feito pra rodar o sistema na AWS (RDS + EC2), pra estudo e pra
compartilhar um link com amigos testarem. Não é infra de produção "de verdade" —
é barata e (propositalmente) simples.

## O que foi construído

- **RDS (Postgres)** — `infra/terraform/rds.tf`. Uma instância `db.t3.micro`,
  hospedando os bancos `auth_db` e `exam_db` (mesmo padrão do `docker-compose.yml`
  local), acessível só do seu IP e da EC2 (via Security Group).
- **EC2** — `infra/terraform/ec2.tf`. Uma instância `t3.small` (2GB RAM — o
  `t3.micro`/1GB não é suficiente pra rodar os 4 serviços Java + RabbitMQ + Redis
  ao mesmo tempo) rodando Docker + Docker Compose, com Elastic IP fixo. Só a porta
  do api-gateway (8086) fica aberta pra internet.
- **`docker-compose.prod.yml`** — igual ao compose local, mas sem container de
  Postgres (usa o RDS) e sem segredos hardcoded (vêm do `.env`, que só existe na EC2,
  nunca é commitado). As imagens **não são construídas na EC2** — são buildadas no
  seu PC e enviadas prontas pro Docker Hub (`lucas6243/ai-exam-*`); a EC2 só dá
  `pull`.
- **S3 (front-end)** — `infra/terraform/s3-frontend.tf`. Um bucket com "Static
  website hosting" ativado, servindo o build do Angular (`front-end/`) em HTTP puro
  (sem HTTPS — veja "Front-end e CORS" abaixo). Link pra compartilhar: output
  `frontend_url`.
- **CORS no `api-gateway`** — como front (S3) e back (EC2) ficam em origens
  diferentes, o `api-gateway` libera explicitamente a origem do S3 via
  `CORS_ALLOWED_ORIGINS` (`SecurityConfig.java`).
- Tudo criado como código em `infra/terraform/` (Terraform), pra poder recriar ou
  destruir com um comando.

## O que aprendemos

- **IAM**: nunca usar a conta root pra automação; criar um usuário dedicado
  (`terraform-ai-exam`) com só as permissões necessárias (RDS, EC2, VPC), autenticado
  via Access Key, não login no console.
- **Terraform**: `init` baixa os providers, `plan` simula sem aplicar, `apply` cria
  de verdade (e começa a cobrar), `destroy` desfaz tudo **e apaga os dados do banco**
  (veja a seção "Parar vs. destruir" abaixo). O estado (`.tfstate`) é local e não vai
  pro git — se ele for apagado/perdido, o Terraform "esquece" o que já existe na AWS.
- **Security Groups** são regras de firewall por recurso — cada um (RDS, EC2) só
  libera exatamente as portas/origens necessárias.
- **RDS** guarda o Postgres gerenciado pela AWS; **EC2** é uma máquina genérica onde
  a gente instala o que quiser (aqui, Docker).
- **Tamanho da instância importa pra RAM, não só pra CPU**: `t3.micro` (1GB) não
  aguenta compilar nem rodar os 4 serviços Java ao mesmo tempo — precisou subir pra
  `t3.small` (2GB). Free Tier cobre só o tamanho `micro`.
- **"Free Plan" da AWS**: contas novas vêm com uma trava que bloqueia qualquer
  instância além do Free Tier, mesmo tendo crédito disponível — precisa fazer
  "Upgrade plan" no Billing Console (não custa nada em si, só remove a trava; você
  continua pagando só pelo que usar).
- **Buildar na EC2 pequena é ruim**: compilar 4 módulos Gradle numa máquina fraca
  pode levar horas (ou travar de vez). Solução: buildar as imagens no seu PC (mais
  potente) e enviar prontas via Docker Hub — a EC2 só baixa e roda.
- **PATH do Windows**: instalar uma ferramenta (AWS CLI, Terraform, ngrok) não a
  deixa disponível em terminais **já abertos** — sempre abrir uma janela nova depois
  de instalar algo novo.
- **IA local via túnel**: dá pra apontar o `ai-generator-service` (rodando na AWS)
  pro Ollama rodando no seu PC, usando `ngrok` pra criar uma URL pública temporária
  até sua máquina. Só funciona enquanto seu PC + Ollama + ngrok estiverem ligados.
- **HTTPS precisa de domínio**: certificados confiáveis (Let's Encrypt, ACM) não são
  emitidos pra um IP solto, só pra domínio. Por isso a API e o S3 ficaram em HTTP
  simples por enquanto — front-end em Vercel/Netlify (HTTPS forçado) bloquearia a
  API via "mixed content" no navegador.
- **Front-end e origem da API**: o Angular usava `apiUrl: '/api'` (relativo),
  pensado pra front e back ficarem na mesma origem via proxy reverso. Hospedar o
  front em outro lugar (S3) exigiu trocar pra URL absoluta
  (`http://<ip-ec2>:8086/api`) + configurar CORS no gateway — sem isso, um preflight
  `OPTIONS` retorna **403** (o próprio Spring Security rejeitando a origem não
  autorizada, não é um bug do endpoint em si).
- **Esquecer de dar `git push`**: depois de mudar código (ex: `SecurityConfig.java`,
  `docker-compose.prod.yml`), a EC2 só reflete a mudança depois de `git pull` lá —
  parece óbvio, mas é fácil esquecer esse passo e ficar sem entender por que uma
  variável de ambiente nova "não chega" no container.

## Comandos pra lembrar

### Como atualizar front-end e back-end — checklist rápido

Front-end (S3) e back-end (EC2) são publicados **separadamente** — atualizar um não
exige atualizar o outro, a menos que a mudança envolva o contrato da API (novo
endpoint, request/response diferente) ou CORS.

**Back-end** (código em `back-end/`, roda na EC2 via Docker Hub):
1. Commitar e dar `git push` das mudanças.
2. No seu PC: `docker compose -f docker-compose.prod.yml build && docker compose -f docker-compose.prod.yml push`
3. Na EC2 (SSH): `git pull && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d`
4. Conferir: `http://<IP_DA_EC2>:8086/actuator/health` deve responder `{"status":"UP"}`.

Detalhes em "Atualizar o código (build local + push + pull na EC2)" mais abaixo.

**Front-end** (código em `front-end/`, servido pelo S3):
1. Se o IP da EC2 mudou, atualizar `apiUrl` em `environment.ts` **antes** de buildar.
2. `npm run build` dentro de `front-end/`.
3. `aws s3 sync "dist/front-end/browser" s3://<bucket> --delete` (nome do bucket via `terraform output frontend_bucket_name`).
4. Testar no link de `terraform output frontend_url` — não precisa invalidar cache, o `--delete` já limpa o que ficou obsoleto.

Detalhes em "Atualizar/publicar o front-end (S3)" mais abaixo.

Se a mudança alterou contrato de API ou CORS: publique o **back-end primeiro**, valide
com `/actuator/health` e `swagger-ui.html`, só depois publique o front — evita testar o
front novo contra uma API antiga que ainda não entende o novo formato.

### Gerenciar a infraestrutura (rodar sempre dentro de `infra/terraform/`)

```bash
cd infra/terraform

terraform plan       # ver o que mudaria, sem aplicar
terraform apply      # criar/atualizar de verdade (custa dinheiro)
terraform destroy    # derrubar TUDO, incluindo os dados do banco (ver aviso abaixo)
terraform output     # ver endpoint do RDS e IP da EC2 atuais
```

A senha do banco, seu IP liberado e o tamanho da instância ficam em
`infra/terraform/terraform.tfvars` (não commitado). Se recriar do zero, copie de
`terraform.tfvars.example`.

### Conectar na EC2

Forma direta:
```bash
ssh -i "$env:USERPROFILE\.ssh\id_ed25519" ec2-user@$(terraform output -raw ec2_public_ip)
```

Forma simplificada — crie/edite `C:\Users\<seu-usuario>\.ssh\config` (arquivo pessoal,
fora do repo, com o IP atual da EC2):
```
Host ai-exam
    HostName <IP_DA_EC2_ATUAL>
    User ec2-user
    IdentityFile ~/.ssh/id_ed25519
```
E depois é só `ssh ai-exam`. Atualize o `HostName` sempre que recriar a EC2 (IP muda).

### Atualizar o código (build local + push + pull na EC2)

No seu PC, depois de commitar/dar push das mudanças:
```bash
docker login -u lucas6243
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml push
```

Na EC2 (por SSH):
```bash
cd stu-ai-backend
git pull
docker compose -f docker-compose.prod.yml pull    # baixa as imagens novas
docker compose -f docker-compose.prod.yml up -d   # recria só o que mudou
```

### Operar os serviços na EC2

```bash
docker compose -f docker-compose.prod.yml ps          # ver status
docker compose -f docker-compose.prod.yml logs -f     # ver logs ao vivo
docker compose -f docker-compose.prod.yml down        # parar containers (sem destruir a EC2)
free -h                                               # checar memória disponível
```

O `.env` com os segredos reais (senha do RDS, JWT_SECRET, etc.) vive só na EC2,
criado a partir de `.env.prod.example` — nunca é commitado.

### Testar de fora

```
http://<IP_DA_EC2>:8086/actuator/health     # deve responder {"status":"UP"}, sem login
http://<IP_DA_EC2>:8086/swagger-ui.html     # testar endpoints pelo navegador
```
A rota raiz (`/`) exige um JWT válido — acessá-la direto no navegador aciona o popup
nativo de usuário/senha do navegador, isso é a segurança funcionando, não um erro.

### Criar os bancos no RDS (só na primeira vez, ou depois de recriar o RDS do zero)

```bash
docker run --rm -it postgres:16 psql "postgresql://postgres:SENHA@ENDPOINT_RDS:5432/postgres"
```

```sql
CREATE DATABASE auth_db;
CREATE DATABASE exam_db;
```

### Usar IA local (Ollama) em vez da OpenAI

No seu PC (deixar essas duas janelas abertas o tempo todo que quiser usar):

```bash
ollama list                 # confirmar que o Ollama está rodando e ver o modelo
ngrok http 11434            # abrir túnel público até o Ollama local
```

Copiar a URL que o `ngrok` mostrar (`https://....ngrok-free.dev`) e colocar no
`.env` da EC2 como `OLLAMA_BASE_URL`, com `AI_PROVIDER=ollama` e `OLLAMA_MODEL`
igual ao nome do modelo. Depois:

```bash
docker compose -f docker-compose.prod.yml up -d ai-generator-service
```

A URL muda toda vez que o `ngrok` reinicia — repita esse passo quando isso acontecer.

### Atualizar/publicar o front-end (S3)

No repositório do front-end (`front-end/`), depois de mudar código:
```bash
npm run build
```
Isso gera `dist/front-end/browser/`. Depois, envie pro bucket (do seu PC, com o
AWS CLI já configurado):
```bash
aws s3 sync "dist/front-end/browser" s3://ai-exam-frontend-a70c5047 --delete
```
O `--delete` remove do bucket qualquer arquivo antigo que não exista mais no build
novo. O link não muda entre publicações (`terraform output frontend_url`).

Se o **IP da EC2 mudar** (ex: recriou sem manter o Elastic IP), atualize
`environment.ts` (`apiUrl`) com o novo endereço antes de rodar `npm run build` de
novo.

### CORS — liberar uma nova origem de front-end

Se o bucket S3 for recriado (URL muda) ou você hospedar o front em outro lugar,
atualize `CORS_ALLOWED_ORIGINS` no `.env` da EC2 com a nova origem e reinicie só o
gateway:
```bash
docker compose -f docker-compose.prod.yml up -d api-gateway
```
Não precisa rebuildar a imagem pra isso — é só variável de ambiente. Só precisa
rebuildar/republicar a imagem do `api-gateway` se o **código** do `SecurityConfig`
mudar.

## Parar vs. destruir — não confundir

**`terraform destroy` apaga o RDS de verdade, sem backup.** Se algum amigo já
cadastrou usuário e criou provas, esse dado desaparece pra sempre. Só use `destroy`
quando realmente não precisar mais dos dados (ex: encerrando o estudo de vez).

Pra pausar entre sessões de teste **sem perder dados**, **pare** a EC2 e o RDS em vez
de destruir (pelo Console AWS: selecione o recurso → "Instance state"/"Stop", tanto
em EC2 quanto em RDS). Parado, você só paga um valor pequeno de armazenamento em
disco, sem cobrança de computação. Detalhe: a AWS **religa um RDS parado sozinha
depois de 7 dias** — serve bem pra pausas curtas, não pra ficar meses sem usar.

Pra religar depois de parado: Console AWS → mesmo caminho → "Start", em vez de
`terraform apply` (o `apply` só é necessário se algo foi de fato destruído).

## Checklist pra encerrar de vez (aceita perder os dados)

```bash
cd infra/terraform
terraform destroy
```

Só recrie com `terraform apply` na próxima vez que quiser usar — vai precisar
recriar os bancos (`auth_db`/`exam_db`) e o `.env` na EC2 do zero. O bucket S3 do
front-end também é recriado com um **nome novo** (sufixo aleatório muda) — atualize
`apiUrl` no `environment.ts` se o IP da EC2 mudar, `CORS_ALLOWED_ORIGINS` no `.env`
da EC2 com a nova URL do bucket, e refaça o `npm run build` + `aws s3 sync`.
