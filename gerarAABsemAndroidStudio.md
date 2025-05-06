Sim, **você pode gerar um `.aab` sem o Android Studio**, usando o **Gradle pela linha de comando**, desde que seu projeto Android esteja corretamente configurado (com o `build.gradle` e outras pastas padrão).

---

### ✅ Requisitos:

* Ter o **Android SDK** instalado (com `bundletool` e `gradle`)
* Ter o **Java (JDK)** instalado e no PATH
* Ter seu projeto Android pronto com o `build.gradle`

---

### 🔧 Passo a Passo para gerar `.aab` via Gradle (linha de comando):

1. **Abra o terminal na raiz do seu projeto Android**
   Exemplo: onde está o `settings.gradle`

2. Rode o comando:

   ```bash
   ./gradlew bundleRelease
   ```

   * No Windows:

     ```cmd
     gradlew.bat bundleRelease
     ```

3. O arquivo `.aab` será gerado aqui:

   ```
   app/build/outputs/bundle/release/app-release.aab
   ```

---

### ✅ Assinando o AAB (se ainda não estiver assinado):

Se o `build.gradle` estiver configurado com a assinatura, o `.aab` já sairá assinado.

Se não estiver, você pode usar o `jarsigner` (vem com o JDK) para assinar manualmente, **ou configurar a assinatura no `build.gradle`**:

```groovy
android {
    ...
    signingConfigs {
        release {
            storeFile file("caminho/keystore.jks")
            storePassword "suaSenha"
            keyAlias "seuAlias"
            keyPassword "suaSenha"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            ...
        }
    }
}
```

---

### ❓Quer ajuda para configurar a assinatura no seu `build.gradle`? Posso montar um exemplo com seus dados (sem mostrar as senhas, claro).
