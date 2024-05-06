#!groovy
node {
    //borra el contenido del workspace para que descargue los repositorios y no haya archivos de las corridas anteriores
    sh 'rm -Rf /var/jenkins_home/workspace/test_sql_l/*'
     
    stage('Checkout Proyecto'){
        dir('scripts') {  //checkout de donde se encuentran los scripts 
            git branch: """${branch}""",
            credentialsId: 'JENKINSTERM',
            //url: ''
              url: """${repo}"""
            }
        dir('control'){ //checkout de repositorio donde se encuentra el archivo de control de scripts ejecutados correctamente
            git branch: 'develop',
            credentialsId: 'JENKINSTERM',
            url: 'repo2'
            }
    }
    
    stage('ScriptRunner'){
  	    echo 'SQLPlusRunner running file ldevops@gire.com'
  	    
        def instancia = sh(script: "cd scripts/ci-cd/; cat Jenkinsdb | grep 'instancia'", returnStdout:true) //variable de tipo array que contiene la/s instancias especificadas por el analista/desarrollador en el archivo Jenkinsdb
        instancia=instancia.split(':')[1]
        instancia=instancia.tokenize()
        println instancia
                       
        def folder = sh(script: 'cd scripts/dbscripts/; ls', returnStdout:true)//variable de tipo array que contiene el nombre de las carpetas correspondientes a las instancias
        folder=folder.tokenize()
        println folder
        
        def ejecutados = sh(script: """cd /var/jenkins_home/workspace/test_sql_l/control/lbalonso/; cat ${branch}.json""", returnStdout:true)
        println ejecutados
        
        def ok //se define la variable
        
        //inicio de loop que recorre el array instancia 
        for (int i = 0; i < instancia.size(); i++){//i variable incremental; con el metodo size() se toma la longitud del array que será la cant de vueltas del loop
            println instancia[i] //muestra la instancia correspondiente a la posición del array
            
            def instanciaEjecutada = sh(script: """cd /var/jenkins_home/workspace/test_sql_l/control/lbalonso/; cat ${branch}.json | grep '${instancia[i]}'""", returnStdout:true) //se define la variable de las instancias ejecutadas
            def scriptsEjecutados = instanciaEjecutada.split('=')[1] //se define una nueva variable para identificar a los scripts ejecutados correctamente, y se lo separa de la instancia con el metodo split()[] dentro de los parentesis debe identificar el caracter que separa la instancia de los scripts, entre corchetes identifican 0 lo que esta antes del caracter entre parentesis y 1 lo que esta luego
            scriptsEjecutados = scriptsEjecutados.tokenize() //esa variable se convierte en un array mediante el metodo tokenize()
            println scriptsEjecutados//muestra el contenido del array
          
          //condicional que devuelve verdadero si el contenido en los arrays instancia y folder en la posición que informa el loop es igual
          if(instancia[i] == folder[i]){        
                def scripts = sh(script: """cd scripts/dbscripts/${instancia[i]}/;  ls -l *.sql | awk '{print \$9}'""", returnStdout:true)//variable que cargo con los scripts que están dentro de la carpeta de la instancia
                scripts = scripts.tokenize().sort() //se lo convierte en array y el metodo sort() los ordena
                println scripts //muestra el contenido del array
                     
                def commons = scripts.intersect(scriptsEjecutados) //array que contiene los datos de la intersección entre el array scripts y scriptsEjecutados
                def difference = scripts.plus(scriptsEjecutados) //array que contiene los scipts más scriptsEjecutados
                difference.removeAll(commons) //a esta último array se remueven los scripts que resultaron de la intersección de arrays
                println commons //muestra el contenido del array con los scripts en común
                println difference //array que contiene los scripts que deben ser ejecutados
              
                //loop que recorre el array que contiene los script a ejecutar
                for (int j = 0; j < difference.size(); j++){
                  
                  if ("""${branch}""" == 'develop'){
                      step([$class: 'SQLPlusRunnerBuilder',credentialsId:'JTERMDB', 
                      instance:"""D${instancia[i]}_ALTA""",scriptType:'file', script: """scripts/dbscripts/${instancia[i]}/${difference[j]}""",
                      scriptContent: '']) //ejecución de script
                    } else if ("""${branch}""" == 'lbalonso'/*releas*/){
                      step([$class: 'SQLPlusRunnerBuilder',credentialsId:'JTERMDB', 
                      instance:"""T${instancia[i]}_ALTA""",scriptType:'file', script: """scripts/dbscripts/${instancia[i]}/${difference[j]}""",
                      scriptContent: '']) //ejecución de script
                    } else if ("""${branch}""" == 'master') {
                      step([$class: 'SQLPlusRunnerBuilder',credentialsId:'JTERMDB', 
                      instance:"""${instancia[i]}_ALTA""",scriptType:'file', script: """scripts/dbscripts/${instancia[i]}/${difference[j]}""",
                      scriptContent: '']) //ejecución de script
                    }
                    
                    def nro_script = difference[j].take(3) //variable que contiene el nro del script ejecutado sin el .sql
                    println nro_script
                                           
                    def count_error = sh(script: """cat  ${nro_script}_${instancia[i]}.txt | grep 'ERROR at line' | wc -l""", returnStdout:true) //variable que contiene cantidad de errores detectados
                    count_error = count_error.toInteger()//ese nro es convertido a entero dado que la aplicación lo toma como string
                    println count_error //muestra el contenido
                    
                    println  count_error == 0 //prueba de validación de igualdad 
                    
                    //condicional que es verdadero si la variable es 0 
                    if (count_error == 0){
                        def icorrectos = sh(script: """cd /var/jenkins_home/workspace/test_sql_l/control/lbalonso/; cat ${branch}.txt | grep '${instancia[i]}'""", returnStdout:true)//carga la variable con los scripts ejecutados anteriormente pertenecientes a la instancia
                        def scorrectos = icorrectos.split('=')[1]//esta variable contendra los scripts ejecutados correctamente
                        scorrectos = scorrectos.tokenize() //la variable se transforma en tipo array
                        println scorrectos //muestra el contenido de la variable
                        println icorrectos //muestra el contenido de la variable
                        
                        ok = difference[j]  //se le asigna a la variable el script ejecutado
                        println ok //mustra el contenido de la variable
                        
                        def scr = scorrectos.plus(ok) //esta variable suma los scripts correctos ejecutados anteriormente con el script correcto ejectutado en esta corrida
                        scr = scr.join(' ') //el metodo join elimina las comas del array 
                        println scr //se muestra el contenido de la variable
                        
                        def remplazo = """${instancia[i]}=${scr}\n""" //variable que contiene el tring que tiene la instancia y los scripts correctos
                        println remplazo //muestra el contenido de la variable
                        //proceso de reemplazo de texto
                                                        
                        def ubicacion1 = """/var/jenkins_home/workspace/test_sql_l/control/lbalonso/${branch}.txt"""
                        def filedata = new File("""${ubicacion1}""")  
                        //def newConfig1 = filedata.text.replace("""${icorrectos}""", """${remplazo}""")
                        def newConfig1 = filedata.text.replace("""${icorrectos}""", """${remplazo}""")
                        filedata.text = newConfig1
                        
                        }
                    }
                    
                } 
            }
        sh """cp /var/jenkins_home/workspace/test_sql_l/control/lbalonso/${branch}.txt /var/jenkins_home/workspace/test_sql_l/control/lbalonso/${branch}.json""" //hace copia del archivo de control a un archivo secundario
    }
    
    stage("Commit") { //proceso de commit
            sh('''
                    cd control/
                    git checkout 'develop'
                    git config user.name 'JENKINSTERM'
                    git config user.email 'mail@dominio.com'
                    git add lbalonso/$branch.txt lbalonso/$branch.json && git commit -m "[Jenkins CI] Add build file"
                ''')
        }
    stage("Push"){  //push de los archivos de control al repositorio
        withCredentials([usernamePassword(credentialsId: 'JENKINSTERM', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh('cd control/ ; git push http://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.com/scm/cicd/cicd-sql.git')
        }
    }

    stage ('mail') { //proceso de envío de email
        def mail = sh(script: "cd scripts/ci-cd/; cat Jenkinsdb | grep 'mail'", returnStdout:true) //variable que obtiene los mails cargados en el jankinsfle
            mail=mail.split(':')[1]
            println mail //muestra el contenido de la variable
        
        try {
           }
            
        finally{
            
            archiveArtifacts artifacts: '*.txt', onlyIfSuccessful: true //toma los archivos que resultan de la ejecución
            
            echo 'I will always say Hello again!'
            
            emailext attachLog: false,//no adjunta el log de la ejecución del pipeline 
                attachmentsPattern: '*.txt', //adjunta los files .txt
                from: 'ci-cd@dominio.com',//remitente del correo
                replyTo: 'ldevops@dominio.com',//dirección de respuesta
                to:"""${mail}""",//inserta los mails que se encuentran en la variable para que se los envien
                body: "Revisar el resultado de los scripts ejecutados: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\n en los archivos adjuntos\n Pipeline: ${env.BUILD_URL}", //cuerpo de email
                subject: "Resultados: Job ${env.JOB_NAME}" //titulo de email
            }
    }  
  
}