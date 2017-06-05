#include<stdio.h>
#include<stdlib.h>
#include<string.h>

void write(int val) {
	printf("%d\n", val);
}

int read() {
  int val;
  printf("> ");
  scanf("%d", &val);
  return val;
}

extern size_t strlen(const char *str);

extern char * strdup(const char *str);

extern int strcmp(const char *str1, const char *str2);

char strget(const char *s, size_t i) {
    return s[i];
}

int strset(char *s, size_t i, char c) {
    s[i] = c;
    return 0;
}

char *strsub(const char *s, size_t from, size_t n) {
    char* result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, s + from, n);
    return result;
}

char *strcat(char *l, const char *r) {
    size_t n1 = strlen(l);
    size_t n2 = strlen(r);
    size_t n = n1 + n2;
    char * result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, l, n1);
    memcpy(result + n1, r, n2);
    return result;
}

char *strmake(size_t n, char c) {
    char *result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memset(result, c, n);
    return result;
}