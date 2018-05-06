#include<stdio.h>
#include<stdlib.h>
#include<string.h>

void write(int val_type, int val) {
	printf("%d\n", val);
}

int read() {
  int val;
  printf("> ");
  scanf("%d", &val);
  return val;
}

extern size_t strlen(const char *str);

size_t strlen_2(int str_type, const char *str) {
    return strlen(str);
}

extern char * strdup(const char *str);

char *strdup_2(int str_type, const char *str) {
    return strdup(str);
}

extern int strcmp(const char *str1, const char *str2);

int strcmp_4(int str1_type, const char *str1, int str2_type, const char *str2) {
    return strcmp(str1, str2);
}

char strget(int s_type, const char *s, int i_type, size_t i) {
    return s[i];
}

int strset(int s_type, char *s, int i_type, size_t i, int c_type, char c) {
    s[i] = c;
    return 0;
}

char *strsub(int s_type, const char *s, int from_type, size_t from, int n_type, size_t n) {
    char* result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, s + from, n);
    return result;
}

char *strcat_4(int l_type, char *l, int r_type, const char *r) {
    size_t n1 = strlen(l);
    size_t n2 = strlen(r);
    size_t n = n1 + n2;
    char * result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memcpy(result, l, n1);
    memcpy(result + n1, r, n2);
    return result;
}

char *strmake(int n_type, size_t n, int c_type, char c) {
    char *result = (char*) malloc((n + 1) * sizeof(char));
    result[n] = '\0';
    memset(result, c, n);
    return result;
}