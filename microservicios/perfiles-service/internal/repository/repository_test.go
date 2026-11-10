package repository

import (
	"regexp"
	"testing"
)

func TestNewUUIDProducesRFC4122LikeValue(t *testing.T) {
	id, err := newUUID()
	if err != nil {
		t.Fatalf("newUUID error: %v", err)
	}
	pattern := regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)
	if !pattern.MatchString(id) {
		t.Fatalf("uuid inválido: %s", id)
	}
}

func TestNewStoresDatabaseReference(t *testing.T) {
	repo := New(nil)
	if repo == nil {
		t.Fatal("Repository.New no debe retornar nil")
	}
}
