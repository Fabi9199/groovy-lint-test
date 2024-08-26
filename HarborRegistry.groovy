

class HarborRegistry {

    def script
    String harborEndpoint
    String projectName
    String adminUser
    String adminPassword
    String jumpHost
    String sshConfigFile
    ArrayList<String> createdProjects = []
    ArrayList<String> createdUsers = []

    static PERMISSIONS = [
        'repository': [
            'push': '{\"action\": \"push\",\"resource\": \"repository\"}',
            'pull': '{\"action\": \"pull\",\"resource\": \"repository\"}',
            'list': '{\"action\": \"list\",\"resource\": \"repository\"}'
        ],
        'artifact': [
            'delete': '{\"action\": \"delete\",\"resource\": \"artifact\"}',
            'list': '{\"action\": \"list\",\"resource\": \"artifact\"}',
            'create': '{\"action\": \"create\",\"resource\": \"artifact-label\"}'
        ]
    ]

    HarborRegistry(def script, String harborEndpoint, String projectName, String adminUser, String adminPassword) {
        this.harborEndpoint = harborEndpoint
        this.script = script
        this.projectName = projectName
        this.adminUser = adminUser
        this.adminPassword = adminPassword
    }

    HarborRegistry(def script, String harborEndpoint, String projectName, String adminUser, String adminPassword, String jumpHost, String sshConfigFile) {
        this.harborEndpoint = harborEndpoint
        this.script = script
        this.projectName = projectName
        this.adminUser = adminUser
        this.adminPassword = adminPassword
        this.jumpHost = jumpHost
        this.sshConfigFile = sshConfigFile
    }

    private void executeRequest(String endpoint, String method, ArrayList<String> headers, String body = '') {
        def url = this.harborEndpoint + '/api/v2.0' + endpoint
        def headerString = '-H ' + headers.join(' -H ')
        if (headerString == '-H ') {
            headerString = ''
        }
        def rawRequest = [
            'curl',
            '-k',
            '-s',
            "-X ${method}",
            url,
            '-u ' + this.adminUser + ':' + this.adminPassword,
            headerString,
            '-H "Content-Type: application/json"'
        ]

        if (body != '') rawRequest.push("-d \"${body.replaceAll('"', '\\\\"')}\"")

        def response = ''
        if (this.jumpHost != '') {
            response = this.script.ssh(this.jumpHost, rawRequest.join(' '), this.sshConfigFile, '')
        }else {
            response = this.script.sh(
                label: "Execute Harbor operation '${endpoint}'",
                script: rawRequest.join(' '),
                returnStdout: true
            ).trim()
        }
        return response
    }

    String createUser(ArrayList<String> permissions, String projectName, String username, String password) {
        def joinedPermissions = permissions.join(',')
        def secret = ''
        def name = ''
        def res = this.executeRequest('/robots', 'POST', [], '{\"duration\": -1, \"secret\":\"' + password + '\", \"disable\": false, \"name\": \"' + username + '\", \"level\": \"system\", \"description\": \"Test User\", \"permissions\": [{\"access\": [' + joinedPermissions + '], \"kind\": \"project\", \"namespace\": \"' + projectName + '\"}]}')
        def userResponse = this.script.readJSON(file: '', text: res)
        this.createdUsers.push(userResponse.name)
        return [
            'username': userResponse.name,
            'password': userResponse.secret
        ]
    }

    String createProject() {
        def project = "${projectName}-${UUID.randomUUID()}"
        this.executeRequest('/projects', 'POST', [],
            '{\"project_name\": \"' + project + '\",\"metadata\": {\"public\": \"false\"}}'
        )
        this.createdProjects.push(project)
        return project
    }

}
