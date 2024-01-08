.PHONY: test run prepare

test: test.py prepare
	python test.py

prepare: \
	data \

# data

data: gen.py
	python gen.py data
