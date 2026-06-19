package main

import (
	"log"
	"net/http"
	"os"
)

func main() {
	addr := os.Getenv("RELAY_ADDR")
	if addr == "" {
		addr = ":7777"
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/nostr+json")
		w.Write([]byte(`{"name":"olas-relay","description":"Olas relay"}`))
	})

	log.Printf("olas relay listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("relay: %v", err)
	}
}
