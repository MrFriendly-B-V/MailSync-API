.PHONY: frontend

CARGO=cargo

clean:
	${MAKE} -C frontend clean
	rm -rf target

frontend:
	${MAKE} -C frontend

rust: ${shell find -type f -name \*.sql -name \*.rs}
	${CARGO} build --release