import jenkins.model.Jenkins
import hudson.plugins.sonar.SonarGlobalConfiguration
import hudson.plugins.sonar.SonarInstallation

def instance = Jenkins.getInstance()
def sonarConfig = instance.getDescriptor(SonarGlobalConfiguration.class)

if (sonarConfig != null) {
    def installations = sonarConfig.getInstallations()
    
    if (installations.length > 0) {
        def sonarInstallation = installations[0]
        def internalUrl = System.getenv('SONAR_INTERNAL_URL') ?: 'http://sonarqube:9000'
        def publicUrl = System.getenv('SONAR_HOST_URL') ?: 'http://localhost:9000'
        
        println "Configurando SonarQube:"
        println "  - URL interna (para conexión): ${internalUrl}"
        println "  - URL pública (para enlaces): ${publicUrl}"
        
        // Crear nueva instalación con URL pública para enlaces
        def newInstallation = new SonarInstallation(
            sonarInstallation.getName(),
            publicUrl,  // Esta es la URL que aparecerá en los enlaces
            sonarInstallation.getCredentialsId(),
            sonarInstallation.getServerAuthenticationToken(),
            sonarInstallation.getMojoVersion(),
            sonarInstallation.getAdditionalProperties(),
            sonarInstallation.getAdditionalAnalysisProperties(),
            sonarInstallation.getTriggers()
        )
        
        sonarConfig.setInstallations(newInstallation)
        sonarConfig.save()
        
        println "✓ SonarQube configurado correctamente con URL pública para enlaces"
    } else {
        println "⚠ No hay instalaciones de SonarQube configuradas"
    }
} else {
    println "⚠ SonarGlobalConfiguration no encontrado"
}
