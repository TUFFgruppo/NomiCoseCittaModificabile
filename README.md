# NomiCoseCittaModificabile
mi da molto fastidio che non posso modificarlo e devo per forza mandare una pull request nell'altro


Gioco ispirato da:
https://share.google/BY4zEDbthEvSmDLGa

Come faremo il programma in ordine cronologico:

1) io creo il server e inizio a fare delle funzioni di base per gestire i client e la comunicazione di stringhe
2) carl crea il corpo del gioco all'interno di un thread del server, quindi per ogni client si salva le risposte, le paragona, e le rimanda indietro ai client.
3) io costruisco il client, quindi ci metto dentro delle funzioni in grado di ricevere e mandare stringhe, ed eventualmente anche immagini.
4) agrelli e Leo prendono i dati mandati dal server e costruiscono l'interfaccia grafica e del gioco.

Come apparirà il gioco per un client:

1) apri l'app/fai partire il programma tramite Power Shell
2) piccola interfaccia grafica dove il programma ti chiede di inserire l'IP del server.
3) quando ti sei connesso schermata grafica che dice quante persone si sono connesse.
4) il primo che si è connesso è quello che decide quando parte il gioco
5) il gioco parte, viene decisa una lettera a caso dal server, interfaccia grafica che è il gioco principale (quindi dove inserisci nomi-cose-citta)
6) finisce il tempo, poi i giocatori scelgono quali scelte degli altri giocatori tenere o bocciare.
7) il gioco finisce e si proclama il vincitore
