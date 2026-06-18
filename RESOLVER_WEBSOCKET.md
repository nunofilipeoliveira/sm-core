# 🔧 Resolução de Dependência WebSocket

## Problema
O servidor não está arrancando porque a dependência `spring-boot-starter-websocket` não foi carregada pelo Maven.

## ✅ Solução

### Passo 1: Parar o Servidor
Se o servidor estiver rodando, **PARE** o servidor Java atual (Ctrl+C no terminal de debug).

### Passo 2: Forçar Reload do Maven no VS Code

#### Opção A: Via Command Palette (RECOMENDADO)
1. Pressione `Ctrl+Shift+P`
2. Digite: `Java: Clean Java Language Server Workspace`
3. Confirme a limpeza
4. Digite: `Maven: Update Project`
5. Selecione o projeto `sm-core`

#### Opção B: Via Terminal
```powershell
cd c:\Users\nfoliveira\git\sm-core\sm-core
# Limpar target
Remove-Item -Recurse -Force .\target\
# Baixar dependências
.\mvnw.cmd dependency:resolve
# Compilar
.\mvnw.cmd compile
```

#### Opção C: Via Eclipse (se estiver usando)
1. Clique com botão direito no projeto `sm-core`
2. `Maven` → `Update Project...`
3. Marque `Force Update of Snapshots/Releases`
4. Clique em `OK`

### Passo 3: Verificar Dependências
Após o reload, verifique se as dependências foram baixadas:

```powershell
cd c:\Users\nfoliveira\git\sm-core\sm-core
Get-ChildItem -Recurse .\target\* -Include spring-boot-starter-websocket*.jar
```

Se aparecer um arquivo `.jar`, a dependência foi baixada com sucesso!

### Passo 4: Recompilar
```powershell
cd c:\Users\nfoliveira\git\sm-core\sm-core
.\mvnw.cmd clean compile
```

### Passo 5: Reiniciar Servidor
Execute o servidor novamente.

---

## 🐛 Troubleshooting

### Se o Maven wrapper não funcionar:
```powershell
# Verificar se Java está configurado
java -version

# Se Java não está no PATH, configurar JAVA_HOME
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

### Se ainda não funcionar:
A dependência `spring-boot-starter-websocket` JÁ está no `pom.xml` (linha 99):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

O problema é apenas o IDE não ter recarregado o Maven.

---

## ✅ Verificação Final

Depois de seguir os passos, verifique se o erro sumiu:

1. Abra o arquivo: `TournamentWebSocketService.java`
2. Veja se a linha `import org.springframework.messaging.simp.SimpMessagingTemplate;` **NÃO** está mais vermelha
3. Se ainda estiver vermelha, force reload do projeto Java (Ctrl+Shift+P → "Java: Clean...")

---

## 📞 Status

**Dependência:** ✅ Adicionada ao `pom.xml`  
**Problema:** ⚠️ IDE não recarregou Maven  
**Solução:** 🔄 Force reload conforme instruções acima  

Após resolver, o WebSocket funcionará perfeitamente! 🚀
