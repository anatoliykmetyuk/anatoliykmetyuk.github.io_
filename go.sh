#!/usr/bin/env bash

stack build && (./site clean; ./site watch)