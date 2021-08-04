#
# Copyright 2019 Okta
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
function Install-OktaAwsCli {
    if (Test-Path $HOME\.okta\uptodate) {
        return
    }
    if (Test-Path $HOME\.okta) {
        if (Test-Path $HOME\.okta\*.jar) {
            Remove-Item $HOME\.okta\*.jar
        }
        if (Test-Path $HOME\.okta\config.properties) {
            Remove-Item $HOME\.okta\config.properties
        }
    } else {
        New-Item -ItemType Directory -Path $HOME\.okta -Force | Out-Null
    }
    New-Item -ItemType File -Path $HOME\.okta\uptodate -Force | Out-Null
    # .NET apparently doesn't default to TLS 1.2 and GitHub requires it
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
    $LatestReleaseResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/oktadev/okta-aws-cli-assume-role/releases/latest"
    $Asset = $LatestReleaseResponse.assets | Where-Object { $_.content_type -eq "application/java-archive" }
    $Client = New-Object System.Net.WebClient
    $Client.DownloadFile($Asset.browser_download_url, "$Home\.okta\okta-aws-cli.jar")
    Add-Content -Path $Home/.okta/config.properties -Value "
#OktaAWSCLI
OKTA_ORG=acmecorp.okta.com.changeme.local
OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com.changeme.local/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
OKTA_USERNAME=$env:USERNAME
OKTA_BROWSER_AUTH=true
"
    Add-Content -Path $Home/.okta/logging.properties -Value "
com.amazonaws.auth.profile.internal.BasicProfileConfigLoader = NONE
"
    if (!(Test-Path $profile)) {
        New-Item -Path $profile -ItemType File -Force | Out-Null
    }
    $ProfileContent = Get-Content $profile
    if (!$ProfileContent -or !$ProfileContent.Contains("#OktaAWSCLI")) {
        Add-Content -Path $profile -Value '
#OktaAWSCLI
function Invoke-Java {
    Param([string]$MainClass)
    $InternetOptions = Get-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
    if ($InternetOptions.ProxyEnable) {
        $ProxyStrings = $InternetOptions.ProxyServer.Split(";")
        $Proxies = @{}
        ForEach ($ProxyString in $ProxyStrings) {
            if ($ProxyString.Contains("=")) {
                ($ProxyProtocol,$ProxyServerPort) = $ProxyString.Split("=")
            } else {
                ($ProxyProtocol,$ProxyServerPort) = ("http", $ProxyString)
            }
            ($ProxyHost, $ProxyPort) = $ProxyServerPort.Split(":")
            $Proxies[$ProxyProtocol] = ($ProxyHost, $ProxyPort)
        }
        if ($Proxies.socks) {
            ($ProxyHost, $ProxyPort) = $Proxies.socks
            $ProxyProtocol = "socks"
        } elseif ($Proxies.https) {
            ($ProxyHost, $ProxyPort) = $Proxies.https
            $ProxyProtocol = "https"
        } elseif ($Proxies.http) {
            ($ProxyHost, $ProxyPort) = $Proxies.http
            $ProxyProtocol = "http"
        }
        if ($InternetOptions.ProxyOverride) {
            $NonProxyHosts = [System.String]::Join("|", ($InternetOptions.ProxyOverride.Replace("<local>", "").Split(";") | Where-Object {$_}))
        } else {
            $NonProxyHosts = ""
        }
        if ($ProxyProtocol -eq "socks") {
            java "-Djava.util.logging.config.file=$HOME\.okta\logging.properties" "-DsocksProxyHost=$ProxyHost" "-DsocksProxyPort=$ProxyPort" "-Dhttp.nonProxyHosts=$NonProxyHosts" -classpath $HOME\.okta\okta-aws-cli.jar $MainClass @args
        } else {
            java "-Djava.util.logging.config.file=$HOME\.okta\logging.properties" "-Dhttp.proxyHost=$ProxyHost" "-Dhttp.proxyPort=$ProxyPort" "-Dhttps.proxyHost=$ProxyHost" "-Dhttps.proxyPort=$ProxyPort" "-Dhttp.nonProxyHosts=$NonProxyHosts" -classpath $HOME\.okta\okta-aws-cli.jar $MainClass @args
        }
    } else {
        java "-Djava.util.logging.config.file=$HOME\.okta\logging.properties" -classpath $HOME\.okta\okta-aws-cli.jar $MainClass @args
    }
}

function Invoke-Okta {
    Param([string]$Profile = "default")
    $args[0] = (Get-Command $args[0]).Name
    $OriginalOKTA_PROFILE = $env:OKTA_PROFILE
    try {
        $env:OKTA_PROFILE = $Profile
        Invoke-Java -MainClass com.okta.tools.WithOkta @args
    } finally {
        $env:OKTA_PROFILE = $OriginalOKTA_PROFILE
    }
}
New-Alias -Name withokta -value Invoke-Okta
New-Alias -Name with-okta -value Invoke-Okta

function Get-OktaRoles {
    Invoke-Java -MainClass com.okta.tools.ListRoles
}
New-Alias -Name okta-listroles -value Get-OktaRoles

function Invoke-OktaAws {
    Param([string]$Profile)
    Invoke-Okta -Profile $Profile aws --profile $Profile @args
}
New-Alias -Name okta-aws -value Invoke-OktaAws
New-Alias -Name awscli -value Invoke-OktaAws

function Invoke-OktaSls {
    Param([string]$Profile)
    Invoke-Okta -Profile $Profile sls --stage $Profile @args
}
New-Alias -Name okta-sls -value Invoke-OktaSls
'
    }
}

Install-OktaAwsCli
