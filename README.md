# End-to-End CI/CD: Java → Maven → Docker → ACR → AKS (Jenkins + Ansible)

```
Developer ──push──> GitHub ──webhook──> Jenkins (Azure VM)
                                          ├─ Maven build + tests
                                          ├─ Docker build/tag (<sha>, latest)
                                          ├─ Push ──> Azure Container Registry
                                          └─ Ansible ──kubectl──> AKS (namespace demo, 2 pods)
                                                                    └─ LoadBalancer ──> Browser
```

| Path | Purpose |
|---|---|
| `src/`, `pom.xml` | Spring Boot 3 app (Java 17) + tests |
| `Dockerfile`, `.dockerignore` | Runtime image |
| `Jenkinsfile` | Pipeline (edit `ACR_NAME`) |
| `k8s/` | Namespace, Deployment, Service |
| `ansible/deploy.yml`, `inventory.ini` | Deploys to AKS |
| `ansible/jenkins-setup.yml`, `jenkins-inventory.ini` | Installs Jenkins + tools on the VM |

## 1. Azure resources
```bash
az login
az account set --subscription "Azure for Students"
for p in Microsoft.ContainerService Microsoft.ContainerRegistry Microsoft.Compute Microsoft.Network; do az provider register --namespace $p; done

RG=cicd-demo-rg; LOC=centralindia; ACR=cicddemo$RANDOM; AKS=cicd-demo-aks; SIZE=Standard_B2s
az group create -n $RG -l $LOC
az acr create -g $RG -n $ACR --sku Basic
az aks create -g $RG -n $AKS --node-count 1 --node-vm-size $SIZE --tier free --attach-acr $ACR --generate-ssh-keys

# Service principal for Jenkins (no --sdk-auth; note appId, password, tenant)
RG_ID=$(az group show -n $RG --query id -o tsv)
az ad sp create-for-rbac --name cicd-demo-sp --role Contributor --scopes $RG_ID
az role assignment create --assignee <appId> --role AcrPush --scope $(az acr show -n $ACR --query id -o tsv)
```
If the region or size is rejected, use one the error message allows.

## 2. Jenkins VM
```bash
az vm create -g $RG -n jenkins-vm --image Ubuntu2204 --size $SIZE \
  --admin-username azureuser --generate-ssh-keys --public-ip-sku Standard
az vm open-port -g $RG -n jenkins-vm --port 8080 --priority 1010
az vm show -d -g $RG -n jenkins-vm --query publicIps -o tsv     # = <JENKINS_VM_IP>
```
Put the IP in `ansible/jenkins-inventory.ini`, then from Cloud Shell or any machine with Ansible and the same SSH key:
```bash
ansible-playbook -i ansible/jenkins-inventory.ini ansible/jenkins-setup.yml
```
The last task prints the initial admin password. (Port 22 is opened by `az vm create`.)

## 3. Configure Jenkins (browser: http://<JENKINS_VM_IP>:8080)
1. Paste the admin password → **Install suggested plugins** → create admin user.
2. Manage Jenkins → Credentials → Global → add:
   - **Username with password**, ID `azure-sp`: username = appId, password = service-principal password.
   - **Secret text**, ID `azure-tenant`: the tenant ID.
3. New Item → **Pipeline** → name `cicd-demo` → Pipeline script from SCM → Git → your repo URL, branch `*/main`, script path `Jenkinsfile`. Tick "GitHub hook trigger for GITScm polling".
4. In the GitHub repo: Settings → Webhooks → Add → `http://<JENKINS_VM_IP>:8080/github-webhook/`, content type `application/json`, push events. (Without it, the pipeline still polls every ~2 minutes.)

## 4. Push the code
Edit `ACR_NAME` in `Jenkinsfile`, then:
```bash
git init && git add . && git commit -m "initial commit"
git branch -M main
git remote add origin https://github.com/<user>/<repo>.git
git push -u origin main
```
Click **Build Now** in Jenkins once (or wait for the trigger). All stages should go green.

## 5. Verify (screenshots)
```bash
az aks get-credentials -g $RG -n $AKS --overwrite-existing
kubectl get nodes
kubectl get pods,deploy,svc -n demo
az acr repository show-tags -n $ACR --repository cicd-demo -o table
```
Open `http://<EXTERNAL-IP>`.

## 6. Change and redeploy demo
```bash
sed -i 's/version 1/version 2/' src/main/java/com/example/demo/GreetingService.java
git commit -am "Update to version 2" && git push
```
Jenkins starts automatically; after it finishes refresh the browser (new text and build SHA).

## 7. Clean up
`az group delete -n $RG --yes --no-wait`  (deletes AKS, ACR and the Jenkins VM)

## Viva notes
- Jenkinsfile is declarative: stages Checkout → Build & Test → Docker → Push → Deploy → Verify.
- Image tagged with git SHA for traceability; `latest` also pushed.
- Rolling update with readiness probe = zero downtime.
- Ansible has two roles here: provisioning Jenkins (`jenkins-setup.yml`) and deploying (`deploy.yml`).
- AKS pulls from ACR via `--attach-acr` (AcrPull for the kubelet identity).
- Limitations: Jenkins on HTTP with a public port, service-principal secret stored in Jenkins, no image scanning, single environment.

## Note
Not executed in the authoring environment (Maven Central was unreachable). Run `mvn clean verify` once first.
